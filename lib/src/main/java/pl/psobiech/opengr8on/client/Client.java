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

package pl.psobiech.opengr8on.client;

import java.io.Closeable;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Spliterators.AbstractSpliterator;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Future.State;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.commands.DiscoverCLUsCommand;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.RandomUtil;
import pl.psobiech.opengr8on.util.SocketUtil;
import pl.psobiech.opengr8on.util.SocketUtil.Payload;
import pl.psobiech.opengr8on.util.SocketUtil.UDPSocket;
import pl.psobiech.opengr8on.util.ThreadUtil;

public class Client implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Client.class);

    public static final int COMMAND_PORT = 1234;

    private static final int BUFFER_SIZE = 2048;

    private static final int ESTIMATED_CLUS = 8;

    private final DatagramPacket responsePacket = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);

    protected final ReentrantLock socketLock = new ReentrantLock();

    protected final UDPSocket socket;

    private final ExecutorService executor = ThreadUtil.virtualExecutor("CLUClient");

    protected final Inet4Address localAddress;

    private final Inet4Address broadcastAddress;

    private final int port;

    public Client(Inet4Address localAddress) {
        this(localAddress, IPv4AddressUtil.BROADCAST_ADDRESS, COMMAND_PORT);
    }

    public Client(Inet4Address localAddress, Inet4Address broadcastAddress, int port) {
        this.localAddress = localAddress;
        this.broadcastAddress = broadcastAddress;
        this.port = port;

        this.socket = SocketUtil.udpRandomPort(localAddress);
        this.socket.open();
    }

    public Stream<CLUDevice> discover(CipherKey projectCipherKey, Map<Long, byte[]> privateKeys, Duration timeout, int limit) {
        final byte[] randomBytes = RandomUtil.bytes(Command.RANDOM_BYTES);
        final DiscoverCLUsCommand.Request request = DiscoverCLUsCommand.request(
            projectCipherKey.encrypt(randomBytes), projectCipherKey.getIV(), localAddress
        );

        return broadcastStream(
            CipherKey.DEFAULT_BROADCAST, CipherKey.DEFAULT_BROADCAST.withIV(projectCipherKey.getIV()),
            request,
            broadcastAddress,
            timeout, limit
        )
            .flatMap(payload -> DiscoverCLUsCommand.parse(randomBytes, payload, privateKeys).stream());
    }

    public Stream<Payload> broadcastStream(
        CipherKey requestCipherKey, CipherKey responseCipherKey,
        Command command,
        Inet4Address ipAddress,
        Duration timeout, int limit
    ) {
        final String uuid = uuid(command);
        final Queue<Payload> queue = new ArrayBlockingQueue<>(ESTIMATED_CLUS);

        final Future<Void> future = executor.submit(() -> {
            socketLock.lock();
            try {
                send(uuid, requestCipherKey, ipAddress, command.asByteArray());

                Duration threadTimeout = timeout;

                final List<Payload> results = new ArrayList<>(Math.max(limit == Integer.MAX_VALUE ? -1 : limit, 0));
                do {
                    final long startedAt = System.nanoTime();

                    final Optional<Payload> payloadOptional = awaitResponsePayload(uuid, responseCipherKey, threadTimeout);
                    if (payloadOptional.isPresent()) {
                        final Payload payload = payloadOptional.get();

                        results.add(payload);

                        while (!queue.offer(payload) && !Thread.interrupted()) {
                            Thread.yield();
                        }
                    }

                    threadTimeout = threadTimeout.minusNanos(System.nanoTime() - startedAt);
                } while (threadTimeout.isPositive() && results.size() < limit && !Thread.interrupted());

                return null;
            } finally {
                socketLock.unlock();
            }
        });

        return StreamSupport.stream(
            new AbstractSpliterator<>(ESTIMATED_CLUS, 0) {
                @Override
                public boolean tryAdvance(Consumer<? super Payload> action) {
                    final boolean futureDone = future.isDone();

                    final Payload payload = queue.poll();
                    if (payload != null) {
                        try {
                            action.accept(payload);
                        } catch (Exception e) {
                            LOGGER.error(e.getMessage(), e);
                        }

                        return true;
                    }

                    if (!futureDone) {
                        return true;
                    }

                    if (future.state() == State.FAILED) {
                        LOGGER.error("Unexpected error while streaming broadcast responses", future.exceptionNow());
                    }

                    return false;
                }
            },
            false
        );
    }

    protected void send(String uuid, CipherKey cipherKey, Inet4Address ipAddress, byte[] buffer) {
        final Payload requestPayload = Payload.of(ipAddress, port, buffer);
        LOGGER.trace(
            "%s\t--D->\t%s // %s"
                .formatted(uuid, requestPayload, cipherKey)
        );

        final byte[] encryptedRequest = cipherKey.encrypt(requestPayload.buffer());
        //        LOGGER.trace(
        //            "%s\t--E->\t%s // %s"
        //                .formatted(uuid, Payload.of(ipAddress, port, encryptedRequest), cipherKey)
        //        );

        socketLock.lock();
        try {
            socket.discard(responsePacket);

            final DatagramPacket requestPacket = new DatagramPacket(encryptedRequest, encryptedRequest.length);
            requestPacket.setAddress(requestPayload.address());
            requestPacket.setPort(requestPayload.port());

            socket.send(requestPacket);
        } finally {
            socketLock.unlock();
        }
    }

    protected Optional<Payload> awaitResponsePayload(String uuid, CipherKey responseCipherKey, Duration timeout) {
        final Optional<Payload> encryptedPayload = socket.tryReceive(responsePacket, timeout);
        if (encryptedPayload.isEmpty()) {
            LOGGER.trace(
                "%s\t-----\tTIMEOUT // %s"
                    .formatted(uuid, responseCipherKey)
            );

            return Optional.empty();
        }

        return Client.tryDecrypt(uuid, responseCipherKey, encryptedPayload.get());
    }

    public static Optional<Payload> tryDecrypt(
        String uuid,
        CipherKey responseCipherKey, Payload encryptedPayload
    ) {
        final Optional<Payload> payload = responseCipherKey.decrypt(encryptedPayload.buffer())
                                                           .map(decryptedResponse ->
                                                               Payload.of(
                                                                   encryptedPayload.address(), encryptedPayload.port(),
                                                                   decryptedResponse
                                                               )
                                                           );

        if (LOGGER.isTraceEnabled()) {
            if (payload.isPresent()) {
                LOGGER.trace(
                    "%s\t<-D--\t%s // %s"
                        .formatted(uuid, payload.get(), responseCipherKey)
                );
            } else {
                LOGGER.trace(
                    "%s\t<-E--\t%s // %s"
                        .formatted(uuid, encryptedPayload, responseCipherKey)
                );
            }
        }

        return payload;
    }

    @Override
    public void close() {
        ThreadUtil.close(executor);

        socketLock.lock();
        try {
            IOUtil.closeQuietly(socket);
        } finally {
            socketLock.unlock();
        }
    }

    public static String uuid(Command command) {
        return uuid(UUID.randomUUID(), command.getClass());
    }

    public static String uuid(UUID uuid, Command command) {
        return uuid(uuid, command.getClass());
    }

    public static String uuid(UUID uuid, Class<? extends Command> clazz) {
        final String className = clazz.getName();
        final String[] classParts = className.split("\\.");

        return "%s\t%s".formatted(classParts[classParts.length - 1], uuid);
    }
}
