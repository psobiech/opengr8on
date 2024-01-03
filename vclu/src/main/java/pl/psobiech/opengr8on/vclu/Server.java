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

package pl.psobiech.opengr8on.vclu;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.CLUFiles;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.Client;
import pl.psobiech.opengr8on.client.Command;
import pl.psobiech.opengr8on.client.commands.DiscoverCLUsCommand;
import pl.psobiech.opengr8on.client.commands.ErrorCommand;
import pl.psobiech.opengr8on.client.commands.LuaScriptCommand;
import pl.psobiech.opengr8on.client.commands.ResetCommand;
import pl.psobiech.opengr8on.client.commands.SetIpCommand;
import pl.psobiech.opengr8on.client.commands.SetKeyCommand;
import pl.psobiech.opengr8on.client.commands.StartTFTPdCommand;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.exceptions.UncheckedInterruptedException;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.tftp.TFTPServer;
import pl.psobiech.opengr8on.tftp.TFTPServer.ServerMode;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil.NetworkInterfaceDto;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.util.RandomUtil;
import pl.psobiech.opengr8on.util.SocketUtil;
import pl.psobiech.opengr8on.util.SocketUtil.Payload;
import pl.psobiech.opengr8on.util.SocketUtil.UDPSocket;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.vclu.Main.CluKeys;
import pl.psobiech.opengr8on.vclu.lua.LuaServer;
import pl.psobiech.opengr8on.vclu.lua.LuaServer.LuaThreadWrapper;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;

public class Server implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);

    private static final String CLIENT_REGISTER_METHOD_PREFIX = "SYSTEM:clientRegister(";

    protected static final int BUFFER_SIZE = 2048;

    private static final byte[] EMPTY_BUFFER = new byte[0];

    private static final int TIMEOUT_BROADCAST_MILLIS = 1000;

    private static final int TIMEOUT_MILLIS = 1000;

    private static final int RESTART_RETRIES = 8;

    private static final long RETRY_DELAY = 100L;

    private final DatagramPacket requestPacket = new DatagramPacket(EMPTY_BUFFER, 0);

    private final DatagramPacket responsePacket = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);

    protected final NetworkInterfaceDto networkInterface;

    private final Path rootDirectory;

    private final CLUDevice cluDevice;

    protected final UDPSocket broadcastSocket;

    protected final ReentrantLock socketLock = new ReentrantLock();

    protected final UDPSocket socket;

    private final ScheduledExecutorService executorService = ThreadUtil.executor("cluServer");

    private LuaThreadWrapper luaThread;

    private CipherKey broadcastCipherKey = CipherKey.DEFAULT_BROADCAST;

    private CipherKey currentCipherKey;

    private Future<Void> tftpServerFuture;

    public Server(NetworkInterfaceDto networkInterface, Path rootDirectory, CipherKey projectCipherKey, CLUDevice cluDevice) {
        this.networkInterface = networkInterface;
        this.rootDirectory    = rootDirectory;

        this.currentCipherKey = projectCipherKey;
        this.cluDevice        = cluDevice;

        restartLuaThread();

        this.broadcastSocket = SocketUtil.udp(
            IPv4AddressUtil.parseIPv4("255.255.255.255"),
            //                 networkInterface.getBroadcastAddress(),
            Client.COMMAND_PORT
        );

        this.socket = SocketUtil.udp(networkInterface.getAddress(), Client.COMMAND_PORT);
    }

    public void listen() throws InterruptedException {
        socket.open();
        broadcastSocket.open();

        executorService
            .scheduleAtFixedRate(() -> {
                    try {
                        final UUID uuid = UUID.randomUUID();

                        awaitRequestPayload(String.valueOf(uuid), broadcastSocket, broadcastCipherKey, Duration.ofMillis(TIMEOUT_BROADCAST_MILLIS))
                            .flatMap(payload ->
                                onBroadcastCommand(payload)
                                    .map(pair ->
                                        ImmutableTriple.of(
                                            payload, pair.getLeft(), pair.getRight()
                                        )
                                    )
                            )
                            .ifPresent(triple ->
                                respond(uuid, triple.getLeft(), triple.getMiddle(), triple.getRight())
                            );
                    } catch (Exception e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                },
                TIMEOUT_BROADCAST_MILLIS, TIMEOUT_BROADCAST_MILLIS, TimeUnit.MILLISECONDS
            );

        executorService
            .scheduleAtFixedRate(() -> {
                    try {
                        final UUID uuid = UUID.randomUUID();

                        awaitRequestPayload(String.valueOf(uuid), socket, currentCipherKey, Duration.ofMillis(TIMEOUT_MILLIS))
                            .flatMap(payload ->
                                onCommand(payload)
                                    .map(pair ->
                                        ImmutableTriple.of(
                                            payload, pair.getLeft(), pair.getRight()
                                        )
                                    )
                            )
                            .ifPresent(triple ->
                                respond(uuid, triple.getLeft(), triple.getMiddle(), triple.getRight())
                            );
                    } catch (Exception e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                },
                TIMEOUT_BROADCAST_MILLIS, TIMEOUT_MILLIS, TimeUnit.MILLISECONDS
            );

        // sleep until interrupted
        new CountDownLatch(1).await();
    }

    private void respond(UUID uuid, Payload requestPayload, CipherKey cipherKey, Command command) {
        respond(uuid, cipherKey, requestPayload.address(), requestPayload.port(), command);
    }

    private Optional<Pair<CipherKey, Command>> onBroadcastCommand(Payload payload) {
        final byte[] buffer = payload.buffer();
        if (DiscoverCLUsCommand.requestMatches(buffer)) {
            return onDiscoverCommand(payload);
        }

        if (SetIpCommand.requestMatches(buffer)) {
            return onSetIpCommand(payload);
        }

        return Optional.empty();
    }

    private Optional<Pair<CipherKey, Command>> onDiscoverCommand(Payload payload) {
        final byte[] buffer = payload.buffer();
        final Optional<DiscoverCLUsCommand.Request> requestOptional = DiscoverCLUsCommand.requestFromByteArray(buffer);
        if (requestOptional.isPresent()) {
            final DiscoverCLUsCommand.Request request = requestOptional.get();

            final byte[] encrypted = request.getEncrypted();
            final byte[] iv = request.getIV();
            final byte[] hash = DiscoverCLUsCommand.hash(currentCipherKey.decrypt(encrypted)
                                                                         .orElse(RandomUtil.bytes(Command.RANDOM_SIZE)));

            cluDevice.setCipherKey(
                cluDevice.getCipherKey()
                         .withIV(RandomUtil.bytes(Command.IV_SIZE))
            );

            currentCipherKey = cluDevice.getCipherKey();
            broadcastCipherKey = cluDevice.getCipherKey();

            return Optional.of(
                ImmutablePair.of(
                    CipherKey.DEFAULT_BROADCAST.withIV(iv),
                    DiscoverCLUsCommand.response(
                        currentCipherKey.encrypt(hash),
                        currentCipherKey.getIV(),
                        cluDevice.getSerialNumber(),
                        cluDevice.getMacAddress()
                    )
                )
            );
        }

        return sendError();
    }

    private Optional<Pair<CipherKey, Command>> onCommand(Payload payload) {
        final byte[] buffer = payload.buffer();

        if (SetIpCommand.requestMatches(buffer)) {
            return onSetIpCommand(payload);
        }

        if (SetKeyCommand.requestMatches(buffer)) {
            return onSetKeyCommand(payload);
        }

        if (ResetCommand.requestMatches(buffer)) {
            return onResetCommand(payload);
        }

        if (LuaScriptCommand.requestMatches(buffer)) {
            return onLuaScriptCommand(payload);
        }

        if (StartTFTPdCommand.requestMatches(buffer)) {
            return onStartFTPdCommand(payload);
        }

        return sendError();
    }

    private Optional<Pair<CipherKey, Command>> onSetIpCommand(Payload payload) {
        final byte[] buffer = payload.buffer();
        final Optional<SetIpCommand.Request> requestOptional = SetIpCommand.requestFromByteArray(buffer);
        if (requestOptional.isPresent()) {
            final SetIpCommand.Request request = requestOptional.get();

            if (Objects.equals(cluDevice.getSerialNumber(), request.getSerialNumber())) {
                return Optional.of(
                    ImmutablePair.of(
                        currentCipherKey,
                        SetIpCommand.response(
                            cluDevice.getSerialNumber(),
                            cluDevice.getAddress()
                        )
                    )
                );
            }
        }

        return sendError();
    }

    private Optional<Pair<CipherKey, Command>> onSetKeyCommand(Payload payload) {
        final byte[] buffer = payload.buffer();
        final Optional<SetKeyCommand.Request> requestOptional = SetKeyCommand.requestFromByteArray(buffer);
        if (requestOptional.isPresent()) {
            final SetKeyCommand.Request request = requestOptional.get();

            // final byte[] encrypted = request.getEncrypted(); // Real CLU sends only dummy data
            final byte[] key = request.getKey();
            final byte[] iv = request.getIV();

            final CipherKey newCipherKey = new CipherKey(key, iv);
            updateCipherKey(newCipherKey);
            // if (newCipherKey.decrypt(encrypted).isEmpty()) {
            //    return sendError();
            //}

            broadcastCipherKey = CipherKey.DEFAULT_BROADCAST;

            return Optional.of(
                ImmutablePair.of(
                    currentCipherKey,
                    SetKeyCommand.response()
                )
            );
        }

        return sendError();
    }

    private void updateCipherKey(CipherKey newCipherKey) {
        currentCipherKey = newCipherKey;

        try {
            ObjectMapperFactory.JSON.writerFor(CluKeys.class)
                                    .writeValue(
                                        rootDirectory.resolve("../keys.json").toFile(),
                                        new CluKeys(
                                            currentCipherKey.getSecretKey(), currentCipherKey.getIV(),
                                            cluDevice.getIv(), cluDevice.getPrivateKey()
                                        )
                                    );
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    private Optional<Pair<CipherKey, Command>> onResetCommand(Payload payload) {
        final byte[] buffer = payload.buffer();
        final Optional<ResetCommand.Request> requestOptional = ResetCommand.requestFromByteArray(buffer);
        if (requestOptional.isPresent()) {
            final ResetCommand.Request request = requestOptional.get();

            restartLuaThread();

            return Optional.of(
                ImmutablePair.of(
                    currentCipherKey,
                    ResetCommand.response(
                        cluDevice.getAddress()
                    )
                )
            );
        }

        return sendError();
    }

    private void restartLuaThread() {
        LOGGER.info("VCLU is starting...");
        FileUtil.closeQuietly(this.luaThread);

        this.luaThread = LuaServer.create(networkInterface, rootDirectory, cluDevice, currentCipherKey, CLUFiles.MAIN_LUA);
        this.luaThread.start();

        LuaError lastException;
        int retries = RESTART_RETRIES;
        do {
            try {
                this.luaThread.globals()
                              .load("return %s".formatted(LuaScriptCommand.CHECK_ALIVE))
                              .call();

                return;
            } catch (LuaError e) {
                lastException = e;

                try {
                    Thread.sleep(RETRY_DELAY);
                } catch (InterruptedException e2) {
                    Thread.currentThread().interrupt();

                    throw new UncheckedInterruptedException(e2);
                }
            }
        } while (retries-- > 0);

        LOGGER.error("Could not start VCLU...", lastException);

        LOGGER.info("Entering VCLU emergency mode!");
        FileUtil.closeQuietly(this.luaThread);

        this.luaThread = LuaServer.create(networkInterface, rootDirectory, cluDevice, currentCipherKey, CLUFiles.EMERGNCY_LUA);
        this.luaThread.start();
    }

    private Optional<Pair<CipherKey, Command>> onLuaScriptCommand(Payload payload) {
        final byte[] buffer = payload.buffer();
        final Optional<LuaScriptCommand.Request> requestOptional = LuaScriptCommand.requestFromByteArray(buffer);
        if (requestOptional.isPresent()) {
            final LuaScriptCommand.Request request = requestOptional.get();

            // when having docker network interfaces,
            // OM often picks incorrect/unreachable local address,
            // so we need to also save real remote address from udp packet
            String script = request.getScript();
            if (script.startsWith(CLIENT_REGISTER_METHOD_PREFIX)) {
                final String remoteAddress = payload.address().getHostAddress();

                script = CLIENT_REGISTER_METHOD_PREFIX + "\"" + remoteAddress + "\", " + script.substring(CLIENT_REGISTER_METHOD_PREFIX.length());
            }

            LuaValue luaValue;
            try {
                luaValue = luaThread.globals()
                                    .load("return %s".formatted(script))
                                    .call();
            } catch (LuaError e) {
                LOGGER.error(e.getMessage(), e);

                luaValue = LuaValue.NIL;
            }

            return Optional.of(
                ImmutablePair.of(
                    currentCipherKey,
                    LuaScriptCommand.response(
                        cluDevice.getAddress(),
                        request.getSessionId(),
                        LuaUtil.stringify(luaValue, "nil")
                    )
                )
            );
        }

        return sendError();
    }

    private Optional<Pair<CipherKey, Command>> onStartFTPdCommand(Payload payload) {
        final byte[] buffer = payload.buffer();
        final Optional<StartTFTPdCommand.Request> requestOptional = StartTFTPdCommand.requestFromByteArray(buffer);
        if (requestOptional.isPresent()) {
            if (tftpServerFuture == null) {
                try {
                    tftpServerFuture = TFTPServer.create(
                        networkInterface.getNetworkInterface(), rootDirectory, Client.TFTP_PORT, ServerMode.GET_AND_REPLACE
                    );
                } catch (Exception e) {
                    throw new UnexpectedException(e);
                }
            }

            return Optional.of(
                ImmutablePair.of(
                    currentCipherKey,
                    StartTFTPdCommand.response()
                )
            );
        }

        return sendError();
    }

    private Optional<Pair<CipherKey, Command>> sendError() {
        return Optional.of(
            ImmutablePair.of(
                currentCipherKey,
                ErrorCommand.response()
            )
        );
    }

    protected Optional<Payload> awaitRequestPayload(String uuid, UDPSocket socket, CipherKey responseCipherKey, Duration timeout) {
        final Optional<Payload> encryptedPayload = socket.tryReceive(responsePacket, timeout);
        if (encryptedPayload.isEmpty()) {
            return Optional.empty();
        }

        return Client.tryDecrypt(uuid, responseCipherKey, encryptedPayload.get());
    }

    protected void respond(UUID uuid, CipherKey cipherKey, Inet4Address ipAddress, int port, Command command) {
        respond(command.uuid(uuid), cipherKey, ipAddress, port, command.asByteArray());
    }

    protected void respond(String uuid, CipherKey cipherKey, Inet4Address ipAddress, int port, byte[] buffer) {
        final Payload requestPayload = Payload.of(ipAddress, port, buffer);
        LOGGER.trace(
            "\n%s\n--D->\t%s // %s"
                .formatted(uuid, requestPayload, cipherKey)
        );

        final byte[] encryptedRequest = cipherKey.encrypt(requestPayload.buffer());
        //        LOGGER.trace(
        //            "\n%s\n--E->\t%s // %s"
        //                .formatted(uuid, Payload.of(ipAddress, port, encryptedRequest), cipherKey)
        //        );

        socketLock.lock();
        try {
            requestPacket.setData(encryptedRequest);
            requestPacket.setAddress(requestPayload.address());
            requestPacket.setPort(requestPayload.port());

            socket.send(requestPacket);

            requestPacket.setData(EMPTY_BUFFER);
        } finally {
            socketLock.unlock();
        }
    }

    @Override
    public void close() {
        if (tftpServerFuture != null) {
            tftpServerFuture.cancel(true);
        }

        socketLock.lock();
        try {
            socket.close();
        } finally {
            socketLock.unlock();
        }

        broadcastSocket.close();

        luaThread.close();

        executorService.shutdownNow();
    }
}
