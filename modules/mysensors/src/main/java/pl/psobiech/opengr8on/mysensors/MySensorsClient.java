/*
 * OpenGr8on, open source extensions to systems based on Grenton devices
 * Copyright (C) 2023 Piotr Sobiech
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pl.psobiech.opengr8on.mysensors;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UncheckedInterruptedException;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.mysensors.message.DataMessage;
import pl.psobiech.opengr8on.mysensors.message.InternalMessage;
import pl.psobiech.opengr8on.mysensors.message.InternalMessage.SensorInternalMessageType;
import pl.psobiech.opengr8on.mysensors.message.Message;
import pl.psobiech.opengr8on.mysensors.message.MessageFactory;
import pl.psobiech.opengr8on.mysensors.message.PresentationMessage;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.SocketUtil.TCPClientSocket;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.util.Util;

import static pl.psobiech.opengr8on.mysensors.message.InternalMessage.SensorInternalMessageType.I_CONFIG;
import static pl.psobiech.opengr8on.mysensors.message.InternalMessage.SensorInternalMessageType.I_DISCOVER_REQUEST;
import static pl.psobiech.opengr8on.mysensors.message.InternalMessage.SensorInternalMessageType.I_HEARTBEAT_RESPONSE;
import static pl.psobiech.opengr8on.mysensors.message.InternalMessage.SensorInternalMessageType.I_PRESENTATION;
import static pl.psobiech.opengr8on.mysensors.message.InternalMessage.SensorInternalMessageType.I_VERSION;

public class MySensorsClient implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MySensorsClient.class);

    public static final int DEFAULT_PORT = 5003;

    private static final int MIN_PACKET_SIZE = 7;

    private static final int MAX_PACKET_SIZE = 32;

    private static final int MAX_BUFFER_SIZE = 100;

    private static final int INITIAL_SEND_DELAY_MILLIS = 200;

    private static final int ANY_NODE = 255;

    private final ExecutorService executorService;

    private final TCPClientSocket socket;

    private final long sendDelayNanos = TimeUnit.MILLISECONDS.toNanos(INITIAL_SEND_DELAY_MILLIS);

    private final ReentrantLock sendLock = new ReentrantLock();

    private Future<?> readFuture;

    private volatile long lastSentAt = 0;

    public MySensorsClient(
        ExecutorService executorService,
        TCPClientSocket socket
    ) {
        this.executorService = executorService;

        this.socket = socket;
    }

    public void open(
        BiFunction<MySensorsClient, InternalMessage, Integer> registrationConsumer,
        BiConsumer<MySensorsClient, PresentationMessage> presentationConsumer,
        BiConsumer<MySensorsClient, DataMessage> dataConsumer
    ) {
        ThreadUtil.cancel(readFuture);

        socket.close();
        socket.open();

        readFuture = executorService.submit(() -> {
            int offset = 0;
            final byte[] buffer = new byte[MAX_BUFFER_SIZE];

            do {
                final UUID uuid = UUID.randomUUID();

                try {
                    final InputStream inputStream = socket.getInputStream();

                    while (inputStream.available() > 0) {
                        final byte value = (byte) inputStream.read();
                        if (isLineEnding(value)) {
                            final String messageAsString = new String(Arrays.copyOf(buffer, offset), StandardCharsets.UTF_8);
                            final Optional<Message> messageOptional = MessageFactory.parseMessage(messageAsString);
                            if (messageOptional.isPresent()) {
                                final Message message = messageOptional.get();

                                LOGGER.trace(
                                    "%s\t<-D--\t%s // %s"
                                        .formatted(uuid, message, messageAsString)
                                );

                                onMessage(
                                    message,
                                    registrationConsumer, presentationConsumer, dataConsumer
                                );
                            } else {
                                LOGGER.warn(
                                    "%s\t<-D--\t%s // %s"
                                        .formatted(uuid, "IGNORING MESSAGE", messageAsString)
                                );
                            }

                            offset = 0;
                            continue;
                        }

                        if (offset == buffer.length) {
                            LOGGER.warn(
                                "%s\t<-D--\t%s"
                                    .formatted(uuid, "INVALID PACKET, DISCARDING BUFFER")
                            );

                            discardBuffer(inputStream);

                            offset = 0;
                            continue;
                        }

                        buffer[offset++] = value;
                    }

                    Thread.yield();
                } catch (SocketTimeoutException e) {
                    // NOP
                } catch (SocketException e) {
                    if (UncheckedInterruptedException.wasSocketInterrupted(e)) {
                        throw new UncheckedInterruptedException(e);
                    }

                    LOGGER.error(e.getMessage(), e);
                } catch (IOException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            } while (!Thread.interrupted());
        });

        send(
            MessageFactory.internal(
                ANY_NODE, ANY_NODE,
                I_DISCOVER_REQUEST
            )
        );
    }

    private void onMessage(
        Message message,
        BiFunction<MySensorsClient, InternalMessage, Integer> registrationConsumer,
        BiConsumer<MySensorsClient, PresentationMessage> presentationConsumer,
        BiConsumer<MySensorsClient, DataMessage> dataConsumer
    ) {
        if (message instanceof PresentationMessage presentationMessage) {
            executorService.submit(() -> presentationConsumer.accept(this, presentationMessage));
        }

        if (message instanceof DataMessage dataMessage) {
            executorService.submit(() -> dataConsumer.accept(this, dataMessage));
        }

        if (message instanceof InternalMessage internalMessage) {
            final SensorInternalMessageType typeEnum = internalMessage.getTypeEnum();

            final Optional<Message> responseMessage = switch (typeEnum) {
                case I_HEARTBEAT_REQUEST -> Optional.of(
                    MessageFactory.internal(
                        message.getNodeId(), ANY_NODE,
                        I_HEARTBEAT_RESPONSE,
                        (int) (System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime())
                    )
                );
                case I_ID_REQUEST -> Optional.of(
                    MessageFactory.idResponse(
                        message.getNodeId(), message.getChildSensorId(),
                        registrationConsumer.apply(this, internalMessage)
                    )
                );
                case I_GATEWAY_READY -> Optional.of(
                    MessageFactory.internal(
                        message.getNodeId(), ANY_NODE,
                        I_VERSION
                    )
                );
                case I_CONFIG -> Optional.of(
                    MessageFactory.internal(
                        message.getNodeId(), message.getChildSensorId(),
                        I_CONFIG,
                        "M" // METRIC
                    )
                );
                case I_DISCOVER_RESPONSE -> Optional.of(
                    MessageFactory.internal(
                        message.getNodeId(), ANY_NODE,
                        I_PRESENTATION
                    )
                );
                default -> Optional.empty();
            };

            responseMessage.ifPresent(this::send);
        }

        if (message.requiresEcho()) {
            echo(message);
        }
    }

    /**
     * Discards all buffered data (if newline is not observed first) - should hopefully reset our state
     */
    private static void discardBuffer(InputStream inputStream) throws IOException {
        while (inputStream.available() > 0) {
            final byte discardedValue = (byte) inputStream.read();
            if (isLineEnding(discardedValue)) {
                return;
            }
        }
    }

    private static boolean isLineEnding(byte value) {
        return value == FileUtil.LF_CODE_POINT || value == FileUtil.CR_CODE_POINT;
    }

    public void send(Message message) {
        try {
            sendAsync(message).get();
        } catch (InterruptedException e) {
            throw new UncheckedInterruptedException(e);
        } catch (ExecutionException e) {
            throw new UnexpectedException(e.getCause());
        }
    }

    public Future<?> sendAsync(Message message) {
        return executorService.submit(() ->
            send(message, false)
        );
    }

    public void echo(Message message) {
        send(message, true);
    }

    private void send(Message message, boolean ignoreEcho) {
        final String messageAsString = message.serialize(ignoreEcho);
        final byte[] messageAsBytes = messageAsString.getBytes(StandardCharsets.UTF_8);

        sendLock.lock();
        try {
            final long waitLeftNanos = (sendDelayNanos - (System.nanoTime() - lastSentAt));
            if (waitLeftNanos > 0) {
                // Controller specification requires to sleep in between sending messages
                //
                // https://www.mysensors.org/controller/requirements
                // https://github.com/mysensors/MySensors/issues/1529
                Util.sleepNanos(waitLeftNanos);
            }

            LOGGER.trace(
                "%s\t--D->\t%s // %s"
                    .formatted(UUID.randomUUID(), message, messageAsString)
            );

            lastSentAt = System.nanoTime();
            socket.send(messageAsBytes, new byte[] {FileUtil.LF_CODE_POINT});
        } catch (SocketException e) {
            if (UncheckedInterruptedException.wasSocketInterrupted(e)) {
                throw new UncheckedInterruptedException(e);
            }

            throw new UnexpectedException(e);
        } catch (IOException e) {
            throw new UnexpectedException(e);
        } finally {
            sendLock.unlock();
        }
    }

    @Override
    public void close() {
        ThreadUtil.cancel(readFuture);

        IOUtil.closeQuietly(socket);
    }
}
