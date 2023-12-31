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

package pl.psobiech.opengr8on.client;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

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
import pl.psobiech.opengr8on.org.apache.commons.net.TFTP;
import pl.psobiech.opengr8on.org.apache.commons.net.TFTPErrorPacket;
import pl.psobiech.opengr8on.org.apache.commons.net.TFTPPacketIOException;
import pl.psobiech.opengr8on.util.HexUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil.NetworkInterfaceDto;
import pl.psobiech.opengr8on.util.RandomUtil;
import pl.psobiech.opengr8on.util.SocketUtil;
import pl.psobiech.opengr8on.util.SocketUtil.Payload;
import pl.psobiech.opengr8on.util.Util;

public class CLUClient extends Client implements Closeable {
    private static Logger LOGGER = LoggerFactory.getLogger(CLUClient.class);

    private static final String TFTP_NOT_FOUND_ERROR_CODE = "Error code %d".formatted(TFTPErrorPacket.FILE_NOT_FOUND);

    private static final int TFTP_RETRIES = 3;

    private static final Duration DEFAULT_TIMEOUT_DURATION = Duration.ofMillis(SocketUtil.DEFAULT_TIMEOUT);

    private final CLUDevice cluDevice;

    private final ReentrantLock tftpClientLock = new ReentrantLock();

    private final TFTPClient tftpClient;

    private CipherKey cipherKey;

    public CLUClient(NetworkInterfaceDto networkInterface, CLUDevice cluDevice) {
        this(networkInterface, cluDevice, cluDevice.getCipherKey());
    }

    public CLUClient(NetworkInterfaceDto networkInterface, Inet4Address ipAddress, CipherKey cipherKey) {
        this(networkInterface, ipAddress, cipherKey, COMMAND_PORT);
    }

    public CLUClient(NetworkInterfaceDto networkInterface, Inet4Address ipAddress, CipherKey cipherKey, int port) {
        this(
            networkInterface,
            new CLUDevice(ipAddress, CipherTypeEnum.PROJECT),
            cipherKey,
            port
        );
    }

    public CLUClient(NetworkInterfaceDto networkInterface, CLUDevice cluDevice, CipherKey cipherKey) {
        this(networkInterface, cluDevice, cipherKey, COMMAND_PORT);
    }

    public CLUClient(NetworkInterfaceDto networkInterface, CLUDevice cluDevice, CipherKey cipherKey, int port) {
        super(networkInterface, port);

        this.cluDevice = cluDevice;

        this.tftpClient = createTFTPClient();

        this.cipherKey = cipherKey;
    }

    private static TFTPClient createTFTPClient() {
        final TFTPClient tftpClient = new TFTPClient();
        tftpClient.setMaxTimeouts(TFTP_RETRIES);
        tftpClient.setDefaultTimeout(DEFAULT_TIMEOUT_DURATION.dividedBy(TFTP_RETRIES));

        return tftpClient;
    }

    public Optional<Boolean> setCipherKey(CipherKey newCipherKey) {
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
                                 this.cipherKey = newCipherKey;

                                 return true;
                             })
            );
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
                    final Inet4Address ipAddress = payload.address();
                    cluDevice.setAddress(ipAddress);

                    return Optional.of(payload.address());
                }

                final SetIpCommand.Response response = responseOptional.get();
                final Inet4Address ipAddress = response.getIpAddress();
                cluDevice.setAddress(ipAddress);

                return Optional.of(ipAddress);
            });
    }

    public CLUDevice getCluDevice() {
        return cluDevice;
    }

    public Optional<Boolean> reset(Duration timeout) {
        final ResetCommand.Request command = ResetCommand.request(networkInterface.getAddress());

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
        final LuaScriptCommand.Request command = LuaScriptCommand.request(networkInterface.getAddress(), sessionId, script);

        return request(command, DEFAULT_TIMEOUT_DURATION)
            .flatMap(payload -> LuaScriptCommand.parse(sessionId, payload));
    }

    public Optional<Boolean> startTFTPdServer() {
        return request(StartTFTPdCommand.request(), DEFAULT_TIMEOUT_DURATION)
            .flatMap(payload -> StartTFTPdCommand.responseFromByteArray(payload.buffer()))
            .map(response -> true);
    }

    public Optional<Boolean> stopTFTPdServer() {
        // not implemented? req_stop_ftp req_tftp_stop don't work
        return Optional.of(true);
    }

    public void uploadFile(Path file, String location) {
        tftpClientLock.lock();
        try (
            tftpClient;
            InputStream inputStream = Files.newInputStream(file)
        ) {
            tftpClient.open();

            tftpClient.sendFile(
                location, TFTP.BINARY_MODE,
                inputStream,
                cluDevice.getAddress(), TFTP_PORT
            );
        } catch (IOException e) {
            throw new UnexpectedException(e);
        } finally {
            tftpClientLock.unlock();
        }
    }

    public Optional<Path> downloadFile(String location, Path file) {
        tftpClientLock.lock();
        try (
            tftpClient;
            OutputStream outputStream = Files.newOutputStream(file)
        ) {
            tftpClient.open();

            tftpClient.receiveFile(
                location, TFTP.BINARY_MODE,
                outputStream,
                cluDevice.getAddress(), TFTP_PORT
            );

            return Optional.of(file);
        } catch (IOException e) {
            final boolean fileNotFound;
            if (e instanceof TFTPPacketIOException) {
                fileNotFound = ((TFTPPacketIOException) e).getErrorPacketCode() == TFTPErrorPacket.FILE_NOT_FOUND;
            } else {
                final String message = e.getMessage();

                fileNotFound = (message != null && message.startsWith(TFTP_NOT_FOUND_ERROR_CODE));
            }

            if (fileNotFound) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e2) {
                    LOGGER.warn(e2.getMessage(), e2);
                }

                return Optional.empty();
            }

            throw new UnexpectedException(e);
        } finally {
            tftpClientLock.unlock();
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

    public static class TFTPClient extends pl.psobiech.opengr8on.org.apache.commons.net.TFTPClient implements Closeable {
        // NOP
    }
}
