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
import java.net.SocketException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

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
import pl.psobiech.opengr8on.tftp.TFTP;
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

    private List<CipherKey> unicastCipherKeys;

    private List<CipherKey> broadcastCipherKeys;

    private CipherKey projectCipherKey;

    private final TFTPServer tftpServer;

    public Server(NetworkInterfaceDto networkInterface, Path rootDirectory, CipherKey projectCipherKey, CLUDevice cluDevice) {
        rootDirectory = rootDirectory.toAbsolutePath().normalize();

        this.networkInterface = networkInterface;
        this.rootDirectory    = rootDirectory;

        this.cluDevice = cluDevice;

        this.projectCipherKey = projectCipherKey;

        this.unicastCipherKeys   = List.of(projectCipherKey);
        this.broadcastCipherKeys = List.of(CipherKey.DEFAULT_BROADCAST, projectCipherKey);

        restartLuaThread();

        this.broadcastSocket = SocketUtil.udpListener(
            IPv4AddressUtil.BROADCAST_ADDRESS, // networkInterface.getBroadcastAddress(),
            Client.COMMAND_PORT
        );

        this.socket = SocketUtil.udpListener(networkInterface.getAddress(), Client.COMMAND_PORT);

        this.tftpServer = new TFTPServer(networkInterface.getAddress(), TFTP.DEFAULT_PORT, ServerMode.GET_AND_REPLACE, rootDirectory);
    }

    public void listen() throws InterruptedException {
        socket.open();
        broadcastSocket.open();

        executorService
            .scheduleAtFixedRate(() -> {
                    try {
                        final UUID uuid = UUID.randomUUID();

                        final Optional<Request> requestOptional = awaitRequestPayload(
                            String.valueOf(uuid),
                            broadcastSocket, Duration.ofMillis(TIMEOUT_BROADCAST_MILLIS),
                            broadcastCipherKeys
                        );
                        if (requestOptional.isEmpty()) {
                            return;
                        }

                        final Request request = requestOptional.get();
                        final Optional<Response> responseOptional = onBroadcastCommand(request);
                        if (responseOptional.isEmpty()) {
                            return;
                        }

                        respond(uuid, request, responseOptional.get());
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

                        final Optional<Request> requestOptional = awaitRequestPayload(
                            String.valueOf(uuid),
                            socket, Duration.ofMillis(TIMEOUT_MILLIS),
                            unicastCipherKeys
                        );
                        if (requestOptional.isEmpty()) {
                            return;
                        }

                        final Request request = requestOptional.get();
                        final Optional<Response> responseOptional = onCommand(request);
                        if (responseOptional.isEmpty()) {
                            return;
                        }

                        respond(uuid, request, responseOptional.get());
                    } catch (Exception e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                },
                TIMEOUT_BROADCAST_MILLIS, TIMEOUT_MILLIS, TimeUnit.MILLISECONDS
            );

        // sleep until interrupted
        new CountDownLatch(1).await();
    }

    private Optional<Request> awaitRequestPayload(String uuid, UDPSocket socket, Duration timeout, List<CipherKey> responseCipherKeys) {
        final Optional<Payload> encryptedPayload = socket.tryReceive(responsePacket, timeout);
        if (encryptedPayload.isEmpty()) {
            return Optional.empty();
        }

        for (CipherKey responseCipherKey : responseCipherKeys) {
            final Optional<Payload> decryptedPayload = Client.tryDecrypt(uuid, responseCipherKey, encryptedPayload.get());
            if (decryptedPayload.isPresent()) {
                return Optional.of(
                    new Request(responseCipherKey, decryptedPayload.get())
                );
            }
        }

        return Optional.empty();
    }

    private Optional<Response> onBroadcastCommand(Request request) {
        final Payload payload = request.payload();

        final byte[] buffer = payload.buffer();
        if (DiscoverCLUsCommand.requestMatches(buffer)) {
            final Optional<DiscoverCLUsCommand.Request> commandOptional = DiscoverCLUsCommand.requestFromByteArray(buffer);
            if (commandOptional.isPresent()) {
                return onDiscoverCommand(request, commandOptional.get());
            }
        }

        if (SetIpCommand.requestMatches(buffer)) {
            final Optional<SetIpCommand.Request> commandOptional = SetIpCommand.requestFromByteArray(buffer);
            if (commandOptional.isPresent()) {
                return onSetIpCommand(request, commandOptional.get());
            }
        }

        return Optional.empty();
    }

    private Optional<Response> onDiscoverCommand(Request request, DiscoverCLUsCommand.Request command) {
        final byte[] encrypted = command.getEncrypted();
        final byte[] iv = command.getIV();
        final byte[] hash = DiscoverCLUsCommand.hash(projectCipherKey.decrypt(encrypted)
                                                                     .orElse(RandomUtil.bytes(Command.RANDOM_SIZE)));

        final CipherKey temporaryCipherKey = cluDevice.getCipherKey()
                                                      .withIV(RandomUtil.bytes(Command.IV_SIZE));

        cluDevice.setCipherKey(temporaryCipherKey);
        broadcastCipherKeys = List.of(CipherKey.DEFAULT_BROADCAST, projectCipherKey, temporaryCipherKey);

        LOGGER.warn("Adding temporary CIPHER KEY...");
        unicastCipherKeys = List.of(projectCipherKey, temporaryCipherKey);

        persistKeys();

        return Optional.of(
            new Response(
                CipherKey.DEFAULT_BROADCAST.withIV(iv),
                DiscoverCLUsCommand.response(
                    temporaryCipherKey.encrypt(hash),
                    temporaryCipherKey.getIV(),
                    cluDevice.getSerialNumber(),
                    cluDevice.getMacAddress()
                )
            )
        );
    }

    private Optional<Response> onCommand(Request request) {
        final CipherKey requestCipherKey = request.cipherKey();
        final Payload payload = request.payload();

        final byte[] buffer = payload.buffer();
        if (SetIpCommand.requestMatches(buffer)) {
            final Optional<SetIpCommand.Request> commandOptional = SetIpCommand.requestFromByteArray(buffer);
            if (commandOptional.isPresent()) {
                return onSetIpCommand(request, commandOptional.get());
            }
        }

        if (SetKeyCommand.requestMatches(buffer)) {
            final Optional<SetKeyCommand.Request> commandOptional = SetKeyCommand.requestFromByteArray(buffer);
            if (commandOptional.isPresent()) {
                return onSetKeyCommand(request, commandOptional.get());
            }
        }

        if (ResetCommand.requestMatches(buffer)) {
            final Optional<ResetCommand.Request> commandOptional = ResetCommand.requestFromByteArray(buffer);
            if (commandOptional.isPresent()) {
                return onResetCommand(request, commandOptional.get());
            }
        }

        if (LuaScriptCommand.requestMatches(buffer)) {
            final Optional<LuaScriptCommand.Request> commandOptional = LuaScriptCommand.requestFromByteArray(buffer);
            if (commandOptional.isPresent()) {
                return onLuaScriptCommand(request, commandOptional.get());
            }
        }

        if (requestCipherKey == projectCipherKey) {
            if (StartTFTPdCommand.requestMatches(buffer)) {
                final Optional<StartTFTPdCommand.Request> commandOptional = StartTFTPdCommand.requestFromByteArray(buffer);
                if (commandOptional.isPresent()) {
                    return onStartFTPdCommand(request, commandOptional.get());
                }
            }
        }

        return sendError(request);
    }

    private Optional<Response> onSetIpCommand(Request request, SetIpCommand.Request command) {
        if (Objects.equals(cluDevice.getSerialNumber(), command.getSerialNumber())) {
            return Optional.of(
                new Response(
                    request.cipherKey(),
                    SetIpCommand.response(
                        cluDevice.getSerialNumber(),
                        cluDevice.getAddress()
                    )
                )
            );
        }

        return sendError(request);
    }

    private Optional<Response> onSetKeyCommand(Request request, SetKeyCommand.Request command) {
        // final byte[] encrypted = request.getEncrypted(); // Real CLU sends only dummy data
        final byte[] key = command.getKey();
        final byte[] iv = command.getIV();

        final CipherKey newCipherKey = new CipherKey(key, iv);
        updateProjectCipherKey(newCipherKey);
        // if (newCipherKey.decrypt(encrypted).isEmpty()) {
        //    return sendError();
        //}

        return Optional.of(
            new Response(
                projectCipherKey,
                SetKeyCommand.response()
            )
        );
    }

    private void updateProjectCipherKey(CipherKey newCipherKey) {
        LOGGER.warn("Updating CIPHER KEY...");

        projectCipherKey = newCipherKey;

        unicastCipherKeys   = List.of(projectCipherKey);
        broadcastCipherKeys = List.of(CipherKey.DEFAULT_BROADCAST, projectCipherKey);

        persistKeys();
    }

    private void persistKeys() {
        try {
            ObjectMapperFactory.JSON.writerFor(CluKeys.class)
                                    .writeValue(
                                        rootDirectory.resolve("../keys.json").toFile(),
                                        new CluKeys(
                                            projectCipherKey.getSecretKey(), projectCipherKey.getIV(),
                                            cluDevice.getCipherKey().getIV(), cluDevice.getPrivateKey()
                                        )
                                    );
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    private Optional<Response> onResetCommand(Request request, ResetCommand.Request command) {
        LOGGER.warn("Restoring CIPHER KEY...");
        unicastCipherKeys = List.of(projectCipherKey);

        tftpServer.stop();
        restartLuaThread();

        return Optional.of(
            new Response(
                request.cipherKey(),
                ResetCommand.response(
                    cluDevice.getAddress()
                )
            )
        );
    }

    private void restartLuaThread() {
        LOGGER.info("VCLU is starting...");
        FileUtil.closeQuietly(this.luaThread);

        this.luaThread = LuaServer.create(networkInterface, rootDirectory, cluDevice, projectCipherKey, CLUFiles.MAIN_LUA);
        this.luaThread.start();

        LuaError lastException;
        int retries = RESTART_RETRIES;
        do {
            try {
                luaCall(LuaScriptCommand.CHECK_ALIVE);

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

        this.luaThread = LuaServer.create(networkInterface, rootDirectory, cluDevice, projectCipherKey, CLUFiles.EMERGNCY_LUA);
        this.luaThread.start();
    }

    private LuaValue luaCall(String script) {
        return this.luaThread.globals()
                             .load("return %s".formatted(script))
                             .call();
    }

    private Optional<Response> onLuaScriptCommand(Request request, LuaScriptCommand.Request command) {
        String script = command.getScript();
        if (request.cipherKey() != projectCipherKey) {
            // allow only checkAlive() function if not using project cipher key
            if (!script.equalsIgnoreCase(LuaScriptCommand.CHECK_ALIVE)) {
                return sendError(request);
            }
        }

        // when having docker network interfaces,
        // OM often picks incorrect/unreachable local address,
        // so we need to also save real remote address from udp packet
        if (script.startsWith(CLIENT_REGISTER_METHOD_PREFIX)) {
            final String remoteAddress = request.payload().address().getHostAddress();

            script = CLIENT_REGISTER_METHOD_PREFIX + "\"" + remoteAddress + "\", " + script.substring(CLIENT_REGISTER_METHOD_PREFIX.length());
        }

        LuaValue luaValue;
        try {
            luaValue = luaCall(script);
        } catch (LuaError e) {
            LOGGER.error(e.getMessage(), e);

            luaValue = LuaValue.NIL;
        }

        return Optional.of(
            new Response(
                request.cipherKey(),
                LuaScriptCommand.response(
                    cluDevice.getAddress(),
                    command.getSessionId(),
                    LuaUtil.stringify(luaValue, "nil")
                )
            )
        );
    }

    private Optional<Response> onStartFTPdCommand(Request request, StartTFTPdCommand.Request command) {
        try {
            tftpServer.start();
        } catch (SocketException e) {
            LOGGER.error(e.getMessage(), e);

            return sendError(request);
        }

        return Optional.of(
            new Response(
                request.cipherKey(),
                StartTFTPdCommand.response()
            )
        );
    }

    private Optional<Response> sendError(Request request) {
        return Optional.of(
            new Response(request.cipherKey(), ErrorCommand.response())
        );
    }

    private void respond(UUID uuid, Request request, Response response) {
        respond(
            uuid,
            response.cipherKey(),
            request.payload().address(), request.payload().port(),
            response.command()
        );
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
        tftpServer.close();

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

    private record Request(CipherKey cipherKey, Payload payload) { }

    private record CommandRequest<C extends Command>(CipherKey cipherKey, C command) { }

    private record Response(CipherKey cipherKey, Command command) { }
}
