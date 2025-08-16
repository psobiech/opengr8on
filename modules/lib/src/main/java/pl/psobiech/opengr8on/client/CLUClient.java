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

    private static final Duration DEFAULT_TIMEOUT_DURATION = Duration.ofMillis(SocketUtil.DEFAULT_TIMEOUT_MILLISECONDS);

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
    }

    /**
     * @return current CLU address
     */
    public Inet4Address getAddress() {
        return getCluDevice().getAddress();
    }

    /**
     * Attempts to update the CLU address
     *
     * @return new or old CLU address (in case the address was rejected) or empty() in case of a timeout
     */
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

    /**
     * Attempts to update the CLU cipher key
     *
     * @return true, if the cipher key was update or empty() in case of a timeout
     */
    public Optional<Boolean> updateCipherKey(CipherKey newCipherKey) {
        final byte[] randomBytes = RandomUtil.bytes(Command.RANDOM_BYTES);

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

    /**
     * @return current cipher key used for communication
     */
    public CipherKey getCipherKey() {
        return cipherKey;
    }

    /**
     * Updates the cipher key used for communication
     */
    public void setCipherKey(CipherKey cipherKey) {
        this.cipherKey = cipherKey;
    }

    /**
     * @return current CLU device
     */
    public CLUDevice getCluDevice() {
        return cluDevice;
    }

    /**
     * Attempts to reboot the CLU and waits for the given timeout until the CLU is operational again
     *
     * @return true, if CLU reset succeeded and CLU is again operational
     */
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

    /**
     * @return true, if CLU is operational
     */
    public Optional<Boolean> checkAlive() {
        return execute(LuaScriptCommand.CHECK_ALIVE)
                .map(returnValue ->
                        !"emergency".equals(returnValue)
                                && (
                                Boolean.parseBoolean(returnValue)
                                        || Objects.equals(getCluDevice().getSerialNumber(), HexUtil.asLong(returnValue))
                        )
                );
    }

    /**
     * Attempts to execute LUA script on the CLU
     *
     * @return response of the LUA Script
     */
    public Optional<String> execute(String script) {
        final Integer sessionId = RandomUtil.integer();
        final LuaScriptCommand.Request command = LuaScriptCommand.request(localAddress, sessionId, script);

        return request(command, DEFAULT_TIMEOUT_DURATION)
                .flatMap(payload -> LuaScriptCommand.parse(sessionId, payload));
    }

    /**
     * @return true, if the TFTPd server was started
     */
    public Optional<Boolean> startTFTPdServer() {
        return request(StartTFTPdCommand.request(), DEFAULT_TIMEOUT_DURATION)
                .flatMap(payload -> StartTFTPdCommand.responseFromByteArray(payload.buffer()))
                .map(response -> true);
    }

    /**
     * @return always returns true, because CLUs do not seem to support this command
     */
    public Optional<Boolean> stopTFTPdServer() {
        // not implemented? req_stop_ftp/req_tftp_stop don't work
        return Optional.of(true);
    }

    /**
     * Uploads file from path to the location on the CLU, using TFTPd server (requires the startTFTPdServer command to be issued first). Does not adjust line
     * endings.
     *
     * @param path     path of the file contents
     * @param location remote location, eg. a:\MAIN.LUA
     */
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

    /**
     * Downloads a file from location on the CLU to local path. Does not adjust line endings.
     *
     * @param location remote location, eg. a:\MAIN.LUA
     * @param path     target path for the file contents
     */
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

    /**
     * Sends a command and awaits for a response for the timeout duration
     */
    public Optional<Payload> request(Command command, Duration timeout) {
        return request(cipherKey, command, timeout);
    }

    /**
     * Sends a command and awaits for a response for the timeout duration (using the provided cipher key for decryption)
     */
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

    /**
     * Sends command, without waiting for any response
     */
    public void send(Command command) {
        final String uuid = uuid(command);

        send(uuid, cipherKey, cluDevice.getAddress(), command.asByteArray());
    }
}
