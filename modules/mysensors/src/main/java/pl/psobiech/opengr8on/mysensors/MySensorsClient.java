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
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UncheckedInterruptedException;
import pl.psobiech.opengr8on.mysensors.message.DataMessage.SensorDataType;
import pl.psobiech.opengr8on.mysensors.message.InternalMessage.SensorInternalMessageType;
import pl.psobiech.opengr8on.mysensors.message.Message;
import pl.psobiech.opengr8on.mysensors.message.Message.SensorCommandType;
import pl.psobiech.opengr8on.mysensors.message.SensorPresentationMessage.SensorType;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.SocketUtil.TCPClientSocket;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.util.ToStringUtil;

public class MySensorsClient implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MySensorsClient.class);

    public static final int DEFAULT_PORT = 5003;

    static final int MIN_PACKET_SIZE = 7;

    static final int MAX_PACKET_SIZE = 32;

    static final int MAX_BUFFER_SIZE = 100;

    private static final int MESSAGE_PARTS = 6;

    private final ExecutorService executorService;

    private final TCPClientSocket socket;

    private final LinkedBlockingQueue<Message> readQueue = new LinkedBlockingQueue<>();

    private final ReentrantLock sendLock = new ReentrantLock();

    private Future<?> readFuture;

    public MySensorsClient(ExecutorService executorService, TCPClientSocket socket) {
        this.executorService = executorService;
        this.socket          = socket;
    }

    public void open() {
        socket.open();

        try {
            socket.connect();
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

        readFuture = executorService.submit(() -> {
            int offset = 0;
            final byte[] buffer = new byte[MAX_BUFFER_SIZE];

            do {
                try {
                    final InputStream inputStream = socket.getInputStream();

                    READ_AVAILABLE:
                    while (inputStream.available() > 0) {
                        final byte value = (byte) inputStream.read();
                        if (isLineEnding(value)) {
                            if (offset < MIN_PACKET_SIZE) {
                                LOGGER.warn("IGNORING PACKET: " + ToStringUtil.toString(Arrays.copyOf(buffer, offset)));
                            } else {
                                final String messageAsString = new String(Arrays.copyOf(buffer, offset), StandardCharsets.UTF_8);
                                final Optional<Message> rawMessage = parseMessage(messageAsString);
                                if (rawMessage.isPresent()) {
                                    final Message message = rawMessage.get();

                                    LOGGER.warn("INCOMING: {}", message);
                                    readQueue.offer(message);
                                } else {
                                    LOGGER.warn("IGNORING MESSAGE: " + messageAsString);
                                }
                            }

                            offset = 0;

                            continue;
                        }

                        if (offset == buffer.length) {
                            LOGGER.warn("BUFFER OVERFLOW, DISCARDING WHOLE BUFFER");

                            // discard all buffered data, unless we get a newline, which resets our state - hopefully
                            while (inputStream.available() > 0) {
                                final byte discardedValue = (byte) inputStream.read();
                                if (isLineEnding(discardedValue)) {
                                    break READ_AVAILABLE;
                                }
                            }

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
    }

    private Optional<Message> parseMessage(String messageAsString) {
        final String[] parts = messageAsString.split(";", MESSAGE_PARTS);
        if (parts.length != MESSAGE_PARTS) {
            return Optional.empty();
        }

        final int nodeId = Integer.parseInt(parts[0]);
        final int sensorChildId = Integer.parseInt(parts[1]);

        final int command = Integer.parseInt(parts[2]);

        final int echo = Integer.parseInt(parts[3]);

        final int type = Integer.parseInt(parts[4]);
        final String payload = parts[5];

        return SensorCommandType.byType(command)
                                .flatMap(commandType ->
                                    switch (commandType) {
                                        case C_PRESENTATION -> SensorType.byType(type)
                                                                         .map(sensorType ->
                                                                             Message.presentation(
                                                                                 nodeId, sensorChildId,
                                                                                 echo,
                                                                                 sensorType, payload
                                                                             )
                                                                         );
                                        case C_SET -> SensorDataType.byType(type)
                                                                    .map(sensorDataType ->
                                                                        Message.set(
                                                                            nodeId, sensorChildId,
                                                                            echo,
                                                                            sensorDataType, payload
                                                                        )
                                                                    );
                                        case C_REQ -> SensorDataType.byType(type)
                                                                    .map(sensorDataType ->
                                                                        Message.request(
                                                                            nodeId, sensorChildId,
                                                                            echo,
                                                                            sensorDataType, payload
                                                                        )
                                                                    );
                                        case C_INTERNAL -> SensorInternalMessageType.byType(type)
                                                                                    .map(sensorInternalMessageType ->
                                                                                        Message.internal(
                                                                                            nodeId, sensorChildId,
                                                                                            echo,
                                                                                            sensorInternalMessageType, payload
                                                                                        )
                                                                                    );
                                        default -> Optional.empty();
                                    });
    }

    private static boolean isLineEnding(byte value) {
        return value == FileUtil.LF_CODE_POINT || value == FileUtil.CR_CODE_POINT;
    }

    public Optional<Message> poll() {
        try {
            return Optional.ofNullable(
                readQueue.poll(1, TimeUnit.SECONDS)
            );
        } catch (InterruptedException e) {
            throw new UncheckedInterruptedException(e);
        }
    }

    public void send(Message message) throws IOException {
        send(message, false);
    }

    public void echo(Message message) throws IOException {
        send(message, true);
    }

    private void send(Message message, boolean ignoreEcho) throws IOException {
        LOGGER.warn("SENDING: {}", message);

        send(
            message.getNodeId()
            + ";"
            + message.getChildSensorId()
            + ";"
            + message.getCommand()
            + ";"
            + (ignoreEcho ? 0 : message.getEcho())
            + ";"
            + message.getType()
            + ";"
            + message.getPayload()
        );
    }

    private void send(String messageAsString) throws IOException {
        final byte[] messageAsBytes = messageAsString.getBytes(StandardCharsets.UTF_8);

        final byte[] buffer = Arrays.copyOf(messageAsBytes, messageAsBytes.length + 1);
        buffer[messageAsBytes.length] = FileUtil.LF_CODE_POINT;

        sendLock.lock();
        try {
            socket.send(buffer);

            // TODO: implement the sendDelay properly
            // https://www.mysensors.org/controller/requirements
            // https://github.com/mysensors/MySensors/issues/1529
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new UncheckedInterruptedException(e);
            }
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
