/*
 * OpenGr8on, open source extensions to systems based on Grenton devices
 * Copyright (C) 2023 Piotr Sobiech
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pl.psobiech.opengr8on.vclu;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.org.apache.commons.net.TFTPServer;
import pl.psobiech.opengr8on.org.apache.commons.net.TFTPServer.ServerMode;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil.NetworkInterfaceDto;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.util.RandomUtil;
import pl.psobiech.opengr8on.util.SocketUtil;
import pl.psobiech.opengr8on.util.SocketUtil.Payload;
import pl.psobiech.opengr8on.util.SocketUtil.UDPSocket;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.vclu.LuaServer.LuaThreadWrapper;
import pl.psobiech.opengr8on.vclu.Main.CluKeys;

public class Server implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);

    protected static final int BUFFER_SIZE = 2048;

    private static final byte[] EMPTY_BUFFER = new byte[0];

    private final DatagramPacket requestPacket = new DatagramPacket(EMPTY_BUFFER, 0);

    private final DatagramPacket responsePacket = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);

    protected final NetworkInterfaceDto networkInterface;

    private final Path aDriveDirectory;

    private final CLUDevice cluDevice;

    protected final UDPSocket broadcastSocket;

    protected final UDPSocket socket;

    private final TFTPServer tftpServer;

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1, ThreadUtil.daemonThreadFactory("cluServer"));

    private LuaThreadWrapper luaThread;

    private CipherKey broadcastCipherKey = CipherKey.DEFAULT_BROADCAST;

    private CipherKey currentCipherKey;

    public Server(NetworkInterfaceDto networkInterface, Path aDriveDirectory, CipherKey projectCipherKey, CLUDevice cluDevice) {
        this.networkInterface = networkInterface;
        this.aDriveDirectory = aDriveDirectory;

        this.currentCipherKey = projectCipherKey;
        this.cluDevice = cluDevice;

        this.luaThread = LuaServer.create(networkInterface, aDriveDirectory, cluDevice, currentCipherKey);
        this.luaThread.start();

        try {
            this.tftpServer = new TFTPServer(
                Client.TFTP_PORT, networkInterface.getAddress(),
                ServerMode.GET_AND_REPLACE
            );

            this.broadcastSocket = SocketUtil.udp(
                IPv4AddressUtil.parseIPv4("255.255.255.255"),
                //                 networkInterface.getBroadcastAddress(),
                Client.COMMAND_PORT
            );
            this.socket = SocketUtil.udp(networkInterface.getAddress(), Client.COMMAND_PORT);
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    public void listen() {
        socket.open();
        broadcastSocket.open();

        try {
            this.tftpServer.start(
                aDriveDirectory.toFile()
            );
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }

        executorService
            .scheduleAtFixedRate(() -> {
                    try {
                        final UUID uuid = UUID.randomUUID();

                        awaitRequestPayload(String.valueOf(uuid), broadcastSocket, broadcastCipherKey, Duration.ofMillis(1000))
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
                1000, 1000, TimeUnit.MILLISECONDS
            );

        executorService
            .scheduleAtFixedRate(() -> {
                    try {
                        final UUID uuid = UUID.randomUUID();

                        awaitRequestPayload(String.valueOf(uuid), socket, currentCipherKey, Duration.ofMillis(100))
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
                1000, 100, TimeUnit.MILLISECONDS
            );
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
                         .withIV(RandomUtil.bytes(16))
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

            //final byte[] encrypted = request.getEncrypted(); // Real CLU sends only dummy data
            final byte[] key = request.getKey();
            final byte[] iv = request.getIV();

            final CipherKey newCipherKey = new CipherKey(key, iv);
            updateCipherKey(newCipherKey);
            //if (newCipherKey.decrypt(encrypted).isEmpty()) {
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
                                        aDriveDirectory.resolve("../keys.json").toFile(),
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

            FileUtil.closeQuietly(this.luaThread);
            this.luaThread = LuaServer.create(networkInterface, aDriveDirectory, cluDevice, currentCipherKey);
            this.luaThread.start();

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

    private Optional<Pair<CipherKey, Command>> onLuaScriptCommand(Payload payload) {
        final byte[] buffer = payload.buffer();
        final Optional<LuaScriptCommand.Request> requestOptional = LuaScriptCommand.requestFromByteArray(buffer);
        if (requestOptional.isPresent()) {
            final LuaScriptCommand.Request request = requestOptional.get();

            final LuaValue luaValue;
            try {
                luaValue = luaThread.globals()
                                    .load("return %s".formatted(request.getScript()))
                                    .call();
            } catch (LuaError e) {
                LOGGER.error(e.getMessage(), e);

                return sendError();
            }

            String returnValue;
            if (luaValue.isstring()) {
                returnValue = String.valueOf(luaValue);
            } else {
                returnValue = "nil";
            }

            return Optional.of(
                ImmutablePair.of(
                    currentCipherKey,
                    LuaScriptCommand.response(
                        cluDevice.getAddress(),
                        request.getSessionId(),
                        returnValue
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

        synchronized (this) {
            requestPacket.setData(encryptedRequest);
            requestPacket.setAddress(requestPayload.address());
            requestPacket.setPort(requestPayload.port());

            socket.send(requestPacket);

            requestPacket.setData(EMPTY_BUFFER);
        }
    }

    @Override
    public void close() {
        tftpServer.close();

        synchronized (this) {
            broadcastSocket.close();
        }

        synchronized (this) {
            socket.close();
        }

        luaThread.close();

        executorService.shutdown();
    }
}
