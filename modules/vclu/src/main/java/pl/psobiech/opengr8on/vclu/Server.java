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
import java.util.concurrent.ExecutorService;

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
import pl.psobiech.opengr8on.client.commands.GenerateMeasurementsCommand;
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
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil.NetworkInterfaceDto;
import pl.psobiech.opengr8on.util.RandomUtil;
import pl.psobiech.opengr8on.util.ResourceUtil;
import pl.psobiech.opengr8on.util.SocketUtil;
import pl.psobiech.opengr8on.util.SocketUtil.Payload;
import pl.psobiech.opengr8on.util.SocketUtil.UDPSocket;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.vclu.Main.CluKeys;
import pl.psobiech.opengr8on.vclu.system.lua.LuaThread;
import pl.psobiech.opengr8on.vclu.system.lua.LuaThreadFactory;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;

public class Server implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);

    private static final String CLIENT_REGISTER_METHOD_PREFIX = "SYSTEM:clientRegister(";

    protected static final int BUFFER_SIZE = 2048;

    private static final int TIMEOUT_MILLIS = 1000;

    private static final int RESTART_RETRIES = 8;

    private static final long RETRY_DELAY = 100L;

    private final ExecutorService executor = ThreadUtil.supportsNonBlockingDatagramSockets()
                                             ? ThreadUtil.virtualExecutor("CLUServer")
                                             : ThreadUtil.daemonExecutor("CLUServer");

    private final DatagramPacket responsePacket = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);

    private final Path parentDirectory;

    private final Path rootDirectory;

    private final Path aDriveDirectory;

    private final CLUDevice cluDevice;

    protected final UDPSocket broadcastSocket;

    protected final UDPSocket commandSocket;

    private final UDPSocket responseSocket;

    private LuaThread mainThread;

    private List<CipherKey> unicastCipherKeys;

    private List<CipherKey> broadcastCipherKeys;

    private CipherKey projectCipherKey;

    private final TFTPServer tftpServer;

    private final MqttClient mqttClient = new MqttClient();

    public Server(Path rootDirectory, CipherKey projectCipherKey, NetworkInterfaceDto networkInterface, CLUDevice cluDevice) {
        this(
            rootDirectory, projectCipherKey, cluDevice,
            // TODO: networkInterface.getBroadcastAddress() does not work with OM
            SocketUtil.udpListener(IPv4AddressUtil.BROADCAST_ADDRESS, Client.COMMAND_PORT),
            SocketUtil.udpListener(cluDevice.getAddress(), Client.COMMAND_PORT),
            SocketUtil.udpRandomPort(cluDevice.getAddress()),
            new TFTPServer(cluDevice.getAddress(), TFTP.DEFAULT_PORT, ServerMode.GET_AND_REPLACE, rootDirectory)
        );
    }

    protected Server(
        Path rootDirectory, CipherKey projectCipherKey, CLUDevice cluDevice,
        UDPSocket broadcastSocket,
        UDPSocket commandSocket, UDPSocket responseSocket,
        TFTPServer tftpServer
    ) {
        this.rootDirectory   = rootDirectory.toAbsolutePath().normalize();
        this.parentDirectory = rootDirectory.getParent();
        this.aDriveDirectory = rootDirectory.resolve("a");
        this.cluDevice       = cluDevice;

        this.broadcastSocket = broadcastSocket;
        this.commandSocket   = commandSocket;
        this.responseSocket  = responseSocket;

        this.tftpServer = tftpServer;

        this.projectCipherKey = projectCipherKey;

        this.broadcastCipherKeys = List.of(CipherKey.DEFAULT_BROADCAST, projectCipherKey);
        this.unicastCipherKeys   = List.of(projectCipherKey);
    }

    public void start() {
        commandSocket.open();
        broadcastSocket.open();
        responseSocket.open();

        executor.execute(() -> {
                do {
                    try {
                        final UUID uuid = UUID.randomUUID();

                        final Optional<Request> requestOptional = awaitRequestPayload(
                            String.valueOf(uuid),
                            broadcastSocket, Duration.ofMillis(TIMEOUT_MILLIS),
                            broadcastCipherKeys
                        );
                        if (requestOptional.isEmpty()) {
                            continue;
                        }

                        final Request request = requestOptional.get();
                        final Optional<Response> responseOptional = onBroadcastCommand(uuid, request);
                        if (responseOptional.isEmpty()) {
                            LOGGER.trace(
                                "%s\tIGNORED\t<-D--\t%s // %s"
                                    .formatted(uuid, request.payload(), request.cipherKey())
                            );

                            continue;
                        }

                        respond(uuid, request, responseOptional.get());
                    } catch (UncheckedInterruptedException e) {
                        LOGGER.trace(e.getMessage(), e);

                        break;
                    } catch (Exception e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                } while (!Thread.interrupted());
            }
        );

        executor.execute(() -> {
                do {
                    try {
                        final UUID uuid = UUID.randomUUID();

                        final Optional<Request> requestOptional = awaitRequestPayload(
                            String.valueOf(uuid),
                            commandSocket, Duration.ofMillis(TIMEOUT_MILLIS),
                            unicastCipherKeys
                        );
                        if (requestOptional.isEmpty()) {
                            continue;
                        }

                        final Request request = requestOptional.get();
                        final Optional<Response> responseOptional = onCommand(uuid, request);
                        if (responseOptional.isEmpty()) {
                            LOGGER.trace(
                                "%s\tIGNORED\t<-D--\t%s // %s"
                                    .formatted(uuid, request.payload(), request.cipherKey())
                            );

                            continue;
                        }

                        respond(uuid, request, responseOptional.get());
                    } catch (UncheckedInterruptedException e) {
                        LOGGER.trace(e.getMessage(), e);

                        break;
                    } catch (Exception e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                } while (!Thread.interrupted());
            }
        );

        startClu();
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
            } else {
                LOGGER.trace(
                    "%s\t<-E--\t%s // %s"
                        .formatted(uuid, encryptedPayload.get(), responseCipherKey)
                );
            }
        }

        return Optional.empty();
    }

    private Optional<Response> onBroadcastCommand(UUID uuid, Request request) {
        final Payload payload = request.payload();

        final byte[] buffer = payload.buffer();
        if (DiscoverCLUsCommand.requestMatches(buffer)) {
            final Optional<DiscoverCLUsCommand.Request> commandOptional = DiscoverCLUsCommand.requestFromByteArray(buffer);
            if (commandOptional.isPresent()) {
                return onDiscoverCommand(uuid, request, commandOptional.get());
            }
        }

        if (SetIpCommand.requestMatches(buffer)) {
            final Optional<SetIpCommand.Request> commandOptional = SetIpCommand.requestFromByteArray(buffer);
            if (commandOptional.isPresent()) {
                return onSetIpCommand(true, uuid, request, commandOptional.get());
            }
        }

        return Optional.empty();
    }

    private Optional<Response> onDiscoverCommand(UUID uuid, Request request, DiscoverCLUsCommand.Request command) {
        logCommand(uuid, request, command);

        final byte[] encrypted = command.getEncrypted();
        final byte[] iv = command.getIV();
        final byte[] hash = DiscoverCLUsCommand.hash(projectCipherKey.decrypt(encrypted)
                                                                     .orElse(RandomUtil.bytes(Command.RANDOM_BYTES)));

        final CipherKey temporaryCipherKey = cluDevice.getCipherKey()
                                                      .withIV(RandomUtil.bytes(Command.IV_BYTES));

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

    private Optional<Response> onCommand(UUID uuid, Request request) {
        final CipherKey requestCipherKey = request.cipherKey();
        final Payload payload = request.payload();

        final byte[] buffer = payload.buffer();
        if (SetIpCommand.requestMatches(buffer)) {
            final Optional<SetIpCommand.Request> commandOptional = SetIpCommand.requestFromByteArray(buffer);
            if (commandOptional.isPresent()) {
                return onSetIpCommand(false, uuid, request, commandOptional.get());
            }
        }

        if (SetKeyCommand.requestMatches(buffer)) {
            final Optional<SetKeyCommand.Request> commandOptional = SetKeyCommand.requestFromByteArray(buffer);
            if (commandOptional.isPresent()) {
                return onSetKeyCommand(uuid, request, commandOptional.get());
            }
        }

        if (ResetCommand.requestMatches(buffer)) {
            final Optional<ResetCommand.Request> commandOptional = ResetCommand.requestFromByteArray(buffer);
            if (commandOptional.isPresent()) {
                return onResetCommand(uuid, request, commandOptional.get());
            }
        }

        if (LuaScriptCommand.requestMatches(buffer)) {
            final Optional<LuaScriptCommand.Request> commandOptional = LuaScriptCommand.requestFromByteArray(buffer);
            if (commandOptional.isPresent()) {
                return onLuaScriptCommand(uuid, request, commandOptional.get());
            }
        }

        if (requestCipherKey == projectCipherKey) {
            if (StartTFTPdCommand.requestMatches(buffer)) {
                final Optional<StartTFTPdCommand.Request> commandOptional = StartTFTPdCommand.requestFromByteArray(buffer);
                if (commandOptional.isPresent()) {
                    return onStartFTPdCommand(uuid, request, commandOptional.get());
                }
            }

            if (GenerateMeasurementsCommand.requestMatches(buffer)) {
                final Optional<GenerateMeasurementsCommand.Request> commandOptional = GenerateMeasurementsCommand.requestFromByteArray(buffer);
                if (commandOptional.isPresent()) {
                    return onGenerateMeasurementsCommand(uuid, request, commandOptional.get());
                }
            }

            if (GenerateMeasurementsCommand.responseMatches(buffer)) {
                final Optional<GenerateMeasurementsCommand.Response> commandOptional = GenerateMeasurementsCommand.responseFromByteArray(buffer);
                if (commandOptional.isPresent()) {
                    return onGenerateMeasurementsCommand(uuid, request, commandOptional.get());
                }
            }
        }

        LOGGER.trace(
            "%s\tUNSUPPORTED\t<-D--\t%s // %s"
                .formatted(uuid, request.payload(), request.cipherKey())
        );

        return sendError(request);
    }

    private Optional<Response> onSetIpCommand(boolean broadcast, UUID uuid, Request request, SetIpCommand.Request command) {
        logCommand(uuid, request, command);

        if (Objects.equals(cluDevice.getSerialNumber(), command.getSerialNumber())) {
            // we cant change IP address, so we only accept current ip address
            if (command.getIpAddress().equals(cluDevice.getAddress())) {
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
        }

        // For broadcasts, the message needs to be silently ignored
        if (broadcast) {
            return Optional.empty();
        }

        return sendError(request);
    }

    private Optional<Response> onSetKeyCommand(UUID uuid, Request request, SetKeyCommand.Request command) {
        logCommand(uuid, request, command);

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
            Config.writeKeys(
                parentDirectory,
                new CluKeys(
                    projectCipherKey.getSecretKey(), projectCipherKey.getIV(),
                    cluDevice.getCipherKey().getIV(), cluDevice.getPrivateKey()
                )
            );
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    private Optional<Response> onResetCommand(UUID uuid, Request request, ResetCommand.Request command) {
        logCommand(uuid, request, command);

        restartClu();

        return Optional.of(
            new Response(
                request.cipherKey(),
                ResetCommand.response(
                    cluDevice.getAddress()
                )
            )
        );
    }

    private void restartClu() {
        unicastCipherKeys = List.of(projectCipherKey);

        mqttClient.stop();
        tftpServer.stop();

        startClu();
    }

    protected void startClu() {
        IOUtil.closeQuietly(this.mainThread);

        LOGGER.info("OpenGr8ton VCLU (Version: {}) using {}, listening on {}", ServerVersion.get(), Runtime.version(), commandSocket.getLocalAddress());

        try {
            this.mainThread = LuaThreadFactory.create(rootDirectory, cluDevice, projectCipherKey, CLUFiles.MAIN_LUA);
            this.mainThread.start();

            checkAlive();
        } catch (Exception e) {
            LOGGER.error("Could not start VCLU... Entering VCLU emergency mode!", e);

            IOUtil.closeQuietly(this.mainThread);

            FileUtil.linkOrCopy(
                ResourceUtil.classPath(CLUFiles.EMERGNCY_LUA.getFileName()),
                aDriveDirectory.resolve(CLUFiles.EMERGNCY_LUA.getFileName())
            );

            this.mainThread = LuaThreadFactory.create(rootDirectory, cluDevice, projectCipherKey, CLUFiles.EMERGNCY_LUA);
            this.mainThread.start();

            checkAlive();
        }

        initialize();
    }

    private void checkAlive() {
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
                    throw new UncheckedInterruptedException(e2);
                }
            }
        } while (retries-- > 0);

        throw lastException;
    }

    private void initialize() {
        final VirtualCLU currentClu = mainThread.virtualSystem().getCurrentClu();
        if (currentClu == null) {
            LOGGER.warn("VCLU is not properly initialized...");

            return;
        }

        if (currentClu.isMqttEnabled()) {
            final Path mqttPath = parentDirectory.resolve("mqtt");

            mqttClient.start(
                currentClu.getMqttUrl(), currentClu.getName(),
                mqttPath.resolve("ca.crt"),
                mqttPath.resolve("certificate.crt"), mqttPath.resolve("key.pem"),
                currentClu
            );
        }
    }

    protected LuaValue luaCall(String script) {
        if (this.mainThread == null) {
            throw new UnexpectedException("LUA is not initialized");
        }

        return this.mainThread.luaCall(script);
    }

    private Optional<Response> onLuaScriptCommand(UUID uuid, Request request, LuaScriptCommand.Request command) {
        logCommand(uuid, request, command);

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
                    LuaUtil.stringifyRaw(luaValue, "nil")
                )
            )
        );
    }

    private Optional<Response> onStartFTPdCommand(UUID uuid, Request request, StartTFTPdCommand.Request command) {
        logCommand(uuid, request, command);

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

    private Optional<Response> onGenerateMeasurementsCommand(UUID uuid, Request request, GenerateMeasurementsCommand.Request command) {
        logCommand(uuid, request, command);

        try {
            tftpServer.start();
        } catch (SocketException e) {
            LOGGER.error(e.getMessage(), e);

            return sendError(request);
        }

        return Optional.of(
            new Response(
                projectCipherKey,
                GenerateMeasurementsCommand.response(
                    cluDevice.getAddress(), command.getSessionId(),
                    GenerateMeasurementsCommand.RESPONSE_OK
                )
            )
        );
    }

    private Optional<Response> onGenerateMeasurementsCommand(UUID uuid, Request request, GenerateMeasurementsCommand.Response command) {
        logCommand(uuid, request, command);

        return Optional.of(
            new Response(
                projectCipherKey,
                GenerateMeasurementsCommand.response(
                    cluDevice.getAddress(), command.getSessionId(),
                    GenerateMeasurementsCommand.RESPONSE_OK
                )
            )
        );
    }

    private static void logCommand(UUID uuid, Request request, Command command) {
        LOGGER.trace(
            "%s\t<-D--\t%s // %s"
                .formatted(command.uuid(uuid), request.payload(), request.cipherKey())
        );
    }

    private Optional<Response> sendError(Request request) {
        return Optional.of(
            new Response(request.cipherKey(), ErrorCommand.response())
        );
    }

    private void respond(UUID uuid, Request request, Response response) {
        final Command command = response.command();

        respond(
            command.uuid(uuid),
            response.cipherKey(),
            request.payload().address(), request.payload().port(),
            command.asByteArray()
        );
    }

    protected void respond(String uuid, CipherKey cipherKey, Inet4Address ipAddress, int port, byte[] buffer) {
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

        final DatagramPacket requestPacket = new DatagramPacket(
            encryptedRequest, encryptedRequest.length,
            requestPayload.address(), requestPayload.port()
        );

        responseSocket.send(requestPacket);
    }

    public CLUDevice getDevice() {
        return cluDevice;
    }

    @Override
    public void close() {
        ThreadUtil.closeQuietly(executor);

        IOUtil.closeQuietly(tftpServer, mqttClient, mainThread);
        IOUtil.closeQuietly(commandSocket, broadcastSocket, responseSocket);
    }

    private record Request(CipherKey cipherKey, Payload payload) { }

    private record Response(CipherKey cipherKey, Command command) { }
}
