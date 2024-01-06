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
import java.io.IOException;
import java.net.Inet4Address;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.commands.LuaScriptCommand;
import pl.psobiech.opengr8on.client.commands.ResetCommand;
import pl.psobiech.opengr8on.client.commands.ResetCommand.Response;
import pl.psobiech.opengr8on.client.commands.SetIpCommand;
import pl.psobiech.opengr8on.client.commands.SetKeyCommand;
import pl.psobiech.opengr8on.client.commands.StartTFTPdCommand;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.client.device.CipherTypeEnum;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.tftp.TFTPClient;
import pl.psobiech.opengr8on.tftp.TFTPTransferMode;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.tftp.packets.TFTPErrorType;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.HexUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.RandomUtil;
import pl.psobiech.opengr8on.util.SocketUtil;
import pl.psobiech.opengr8on.util.SocketUtil.Payload;
import pl.psobiech.opengr8on.util.Util;

public class CLUClient extends Client implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CLUClient.class);

    private static final Duration DEFAULT_TIMEOUT_DURATION = Duration.ofMillis(SocketUtil.DEFAULT_TIMEOUT);

    private final CLUDevice cluDevice;

    private final TFTPClient tftpClient;

    private CipherKey cipherKey;

    public CLUClient(Inet4Address localAddress, CLUDevice cluDevice) {
        this(localAddress, cluDevice, cluDevice.getCipherKey());
    }

    public CLUClient(Inet4Address localAddress, Inet4Address ipAddress, CipherKey cipherKey) {
        this(localAddress, ipAddress, cipherKey, COMMAND_PORT);
    }

    public CLUClient(Inet4Address localAddress, Inet4Address ipAddress, CipherKey cipherKey, int port) {
        this(
            localAddress,
            new CLUDevice(ipAddress, CipherTypeEnum.PROJECT),
            cipherKey,
            IPv4AddressUtil.BROADCAST_ADDRESS,
            port
        );
    }

    public CLUClient(Inet4Address localAddress, CLUDevice cluDevice, CipherKey cipherKey) {
        this(localAddress, cluDevice, cipherKey, IPv4AddressUtil.BROADCAST_ADDRESS, COMMAND_PORT);
    }

    public CLUClient(Inet4Address localAddress, CLUDevice cluDevice, CipherKey cipherKey, Inet4Address broadcastAddress, int port) {
        super(localAddress, broadcastAddress, port);

        this.cluDevice = cluDevice;

        this.cipherKey = cipherKey;

        this.tftpClient = new TFTPClient(SocketUtil.udpRandomPort(localAddress));
        tftpClient.open();
    }

    public Inet4Address getAddress() {
        return getCluDevice().getAddress();
    }

    public Optional<Inet4Address> setAddress(Inet4Address newAddress, Inet4Address gatewayAddress) {
        final SetIpCommand.Request command = SetIpCommand.request(cluDevice.getSerialNumber(), newAddress, gatewayAddress);

        return request(command, DEFAULT_TIMEOUT_DURATION)
            .flatMap(payload -> {
                final Optional<SetIpCommand.Response> responseOptional = SetIpCommand.responseFromByteArray(payload.buffer());
                if (responseOptional.isEmpty()) {
                    return Optional.empty();
                }

                final SetIpCommand.Response response = responseOptional.get();
                final Inet4Address ipAddress = response.getIpAddress();
                cluDevice.setAddress(ipAddress);

                return Optional.of(ipAddress);
            });
    }

    public CipherKey getCipherKey() {
        return cipherKey;
    }

    public Optional<Boolean> updateCipherKey(CipherKey newCipherKey) {
        final byte[] randomBytes = RandomUtil.bytes(Command.RANDOM_SIZE);

        return request(
            newCipherKey,
            SetKeyCommand.request(
                newCipherKey.encrypt(randomBytes), newCipherKey.getSecretKey(), newCipherKey.getIV()
            ),
            DEFAULT_TIMEOUT_DURATION
        )
            .flatMap(payload ->
                SetKeyCommand.responseFromByteArray(payload.buffer())
                             .map(response -> {
                                 setCipherKey(newCipherKey);

                                 return true;
                             })
            );
    }

    public void setCipherKey(CipherKey cipherKey) {
        this.cipherKey = cipherKey;
    }

    public CLUDevice getCluDevice() {
        return cluDevice;
    }

    public Optional<Boolean> reset(Duration timeout) {
        final ResetCommand.Request command = ResetCommand.request(localAddress);

        final Optional<Response> reset = request(command, DEFAULT_TIMEOUT_DURATION)
            .flatMap(payload -> ResetCommand.responseFromByteArray(payload.buffer()));

        if (reset.isPresent()) {
            return Optional.of(true);
        }

        return Util.repeatUntilTrueOrTimeout(
            timeout,
            duration ->
                checkAlive()
        );
    }

    public Optional<Boolean> checkAlive() {
        return execute(LuaScriptCommand.CHECK_ALIVE)
            .map(returnValue -> Boolean.parseBoolean(returnValue) || Objects.equals(getCluDevice().getSerialNumber(), HexUtil.asLong(returnValue)));
    }

    public Optional<String> execute(String script) {
        final Integer sessionId = HexUtil.asInt(RandomUtil.hexString(8));
        final LuaScriptCommand.Request command = LuaScriptCommand.request(localAddress, sessionId, script);

        return request(command, DEFAULT_TIMEOUT_DURATION)
            .flatMap(payload -> LuaScriptCommand.parse(sessionId, payload));
    }

    public Optional<Boolean> startTFTPdServer() {
        return request(StartTFTPdCommand.request(), DEFAULT_TIMEOUT_DURATION)
            .flatMap(payload -> StartTFTPdCommand.responseFromByteArray(payload.buffer()))
            .map(response -> true);
    }

    public Optional<Boolean> stopTFTPdServer() {
        // not implemented? req_stop_ftp/req_tftp_stop don't work
        return Optional.of(true);
    }

    public void uploadFile(Path path, String location) {
        try {
            tftpClient.upload(
                cluDevice.getAddress(),
                TFTPTransferMode.OCTET,
                path,
                location
            );
        } catch (IOException | TFTPPacketException e) {
            throw new UnexpectedException(e.getCause());
        }
    }

    public Optional<Path> downloadFile(String location, Path path) {
        try {
            tftpClient.download(
                cluDevice.getAddress(),
                TFTPTransferMode.OCTET,
                location,
                path
            );

            return Optional.of(path);
        } catch (IOException | TFTPPacketException e) {
            FileUtil.deleteQuietly(path);

            final Throwable cause = e.getCause();
            if (cause instanceof TFTPPacketException packetException) {
                if (packetException.getError() == TFTPErrorType.FILE_NOT_FOUND) {
                    FileUtil.deleteQuietly(path);

                    return Optional.empty();
                }
            }

            throw new UnexpectedException(e);
        }
    }

    public void clientReport(String value) {
        final LuaScriptCommand.Response response = LuaScriptCommand.response(cluDevice.getAddress(), HexUtil.asInt(RandomUtil.hexString(8)), value);
        final String uuid = response.uuid(UUID.randomUUID());

        socketLock.lock();
        try {
            send(uuid, cipherKey, cluDevice.getAddress(), response.asByteArray());
        } finally {
            socketLock.unlock();
        }
    }

    public Optional<Payload> request(Command command, Duration timeout) {
        return request(cipherKey, command, timeout);
    }

    public Optional<Payload> request(CipherKey responseCipherKey, Command command, Duration timeout) {
        final String uuid = uuid(command);

        socketLock.lock();
        try {
            send(uuid, cipherKey, cluDevice.getAddress(), command.asByteArray());

            return Util.repeatUntilTimeout(
                timeout,
                duration ->
                    awaitResponsePayload(uuid, responseCipherKey, duration)
            );
        } finally {
            socketLock.unlock();
        }
    }
}
