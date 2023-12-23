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

package pl.psobiech.opengr8on.client.commands;

import java.net.Inet4Address;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.Command;
import pl.psobiech.opengr8on.client.device.CipherTypeEnum;
import pl.psobiech.opengr8on.util.SocketUtil.Payload;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.util.HexUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.Util;

public class DiscoverCLUsCommand {
    private DiscoverCLUsCommand() {
        // NOP
    }

    public static Request request(byte[] encrypted, byte[] iv, Inet4Address ipAddress) {
        return new Request(encrypted, iv, ipAddress);
    }

    public static Optional<Request> requestFromByteArray(byte[] buffer) {
        if (!requestMatches(buffer)) {
            return Optional.empty();
        }

        final byte[] encrypted = Arrays.copyOfRange(buffer, 0, Command.RANDOM_ENCRYPTED_SIZE);
        final byte[] iv = Arrays.copyOfRange(buffer, Command.RANDOM_ENCRYPTED_SIZE + 1, Command.RANDOM_ENCRYPTED_SIZE + 1 + Command.IV_SIZE);

        final String requestAsString = Command.asString(buffer, Command.RANDOM_ENCRYPTED_SIZE + 1 + Command.IV_SIZE + 1);
        final Optional<String[]> requestPartsOptional = Util.splitExact(requestAsString, ":", 2);
        if (requestPartsOptional.isEmpty()) {
            return Optional.empty();
        }

        final String[] requestParts = requestPartsOptional.get();

        return Optional.of(
            new Request(
                encrypted, iv,
                IPv4AddressUtil.parseIPv4(requestParts[1])
            )
        );
    }

    public static boolean requestMatches(byte[] buffer) {
        if (buffer.length < Command.RANDOM_ENCRYPTED_SIZE + 1 + Command.IV_SIZE + 1 + Request.COMMAND.length() + 1 + Command.MIN_IP_SIZE) {
            return false;
        }

        if (buffer[Command.RANDOM_ENCRYPTED_SIZE] != ':'
            || buffer[Command.RANDOM_ENCRYPTED_SIZE + 1 + Command.IV_SIZE] != ':'
            || buffer[Command.RANDOM_ENCRYPTED_SIZE + 1 + Command.IV_SIZE + 1 + Request.COMMAND.length()] != ':') {
            return false;
        }

        return Request.COMMAND.equals(
            Command.asString(
                buffer,
                Command.RANDOM_ENCRYPTED_SIZE + 1 + Command.IV_SIZE + 1,
                Request.COMMAND.length()
            )
        );
    }

    public static Response response(byte[] encrypted, byte[] iv, Long serialNumber, String macAddress) {
        return new Response(encrypted, iv, serialNumber, macAddress);
    }

    public static Optional<CLUDevice> parse(byte[] randomBytes, Payload payload, Map<Long, byte[]> privateKeys) {
        final Optional<Response> responseOptional = responseFromByteArray(payload.buffer());
        if (responseOptional.isEmpty()) {
            return Optional.empty();
        }

        final Response response = responseOptional.get();
        final byte[] iv = response.iv;
        final Long serialNumberAsLong = response.serialNumber;
        final byte[] privateKey = privateKeys.get(serialNumberAsLong);

        final CipherTypeEnum cipherType = getCipherType(randomBytes, response.encrypted, iv, privateKey);

        return Optional.of(
            new CLUDevice(
                serialNumberAsLong, response.macAddress, payload.address(),
                cipherType, iv, privateKey
            )
        );
    }

    public static Optional<Response> responseFromByteArray(byte[] buffer) {
        if (!responseMatches(buffer)) {
            return Optional.empty();
        }

        final byte[] encrypted = Arrays.copyOfRange(buffer, 0, Command.RANDOM_ENCRYPTED_SIZE);
        final byte[] iv = Arrays.copyOfRange(buffer, Command.RANDOM_ENCRYPTED_SIZE + 1, Command.RANDOM_ENCRYPTED_SIZE + 1 + Command.IV_SIZE);

        final String responseAsString = Command.asString(buffer, Command.RANDOM_ENCRYPTED_SIZE + 1 + Command.IV_SIZE + 1);
        final Optional<String[]> responsePartsOptional = Util.splitExact(responseAsString, ":", 3);
        if (responsePartsOptional.isEmpty()) {
            return Optional.empty();
        }

        final String[] responseParts = responsePartsOptional.get();

        return Optional.of(
            new Response(
                encrypted, iv,
                HexUtil.asLong(responseParts[1]),
                responseParts[2]
            )
        );
    }

    public static boolean responseMatches(byte[] buffer) {
        if (buffer.length
            < Command.RANDOM_ENCRYPTED_SIZE + 1 + Command.IV_SIZE + 1 + Response.COMMAND.length() + 1 + Command.MIN_SERIAL_NUMBER_SIZE + 1 + Command.MAC_SIZE) {
            return false;
        }

        if (buffer[32] != ':'
            || buffer[Command.RANDOM_ENCRYPTED_SIZE + 1 + Command.IV_SIZE] != ':'
            || buffer[Command.RANDOM_ENCRYPTED_SIZE + 1 + Command.IV_SIZE + 1 + Response.COMMAND.length()] != ':') {
            return false;
        }

        return Response.COMMAND.equals(
            Command.asString(
                buffer,
                Command.RANDOM_ENCRYPTED_SIZE + 1 + Command.IV_SIZE + 1,
                Response.COMMAND.length()
            )
        );
    }

    public static CipherTypeEnum getCipherType(byte[] randomBytes, byte[] encrypted, byte[] iv, byte[] privateKey) {
        if (privateKey == null) {
            return CipherTypeEnum.UNKNOWN;
        }

        final byte[] randomBytesHash = hash(randomBytes);
        final byte[] randomEncrypted = CipherKey.getInitialCipherKey(iv, privateKey)
                                                .encrypt(randomBytesHash);
        if (Arrays.equals(randomEncrypted, encrypted)) {
            return CipherTypeEnum.PROJECT;
        }

        return CipherTypeEnum.NONE;
    }

    public static byte[] hash(byte[] buffer) {
        final byte[] result = new byte[buffer.length];

        int previousValue = (result[0] = (byte) (buffer[0] ^ buffer[buffer.length - 1]));
        for (int i = 1; i < buffer.length; i++) {
            final int a = previousValue % 0x0D;
            final int b = buffer[i] % 0x13;

            previousValue = (result[i] = (byte) ((a + 1) * (b + 1)));
        }

        return result;
    }

    public static class Request implements Command {
        protected static final String COMMAND = "req_discovery_clu";

        private final byte[] encrypted;

        private final byte[] iv;

        private final Inet4Address ipAddress;

        private Request(byte[] encrypted, byte[] iv, Inet4Address ipAddress) {
            this.encrypted = encrypted;
            this.iv = iv;
            this.ipAddress = ipAddress;
        }

        @Override
        public byte[] asByteArray() {
            return Command.serialize(
                encrypted,
                ":",
                iv,
                ":",
                COMMAND,
                ":",
                ipAddress
            );
        }

        public byte[] getEncrypted() {
            return encrypted;
        }

        public byte[] getIV() {
            return iv;
        }

        public Inet4Address getIpAddress() {
            return ipAddress;
        }
    }

    public static class Response implements Command {
        protected static final String COMMAND = "resp_discovery_clu";

        private final byte[] encrypted;

        private final byte[] iv;

        private final Long serialNumber;

        private final String macAddress;

        private Response(byte[] encrypted, byte[] iv, Long serialNumber, String macAddress) {
            this.encrypted = encrypted;
            this.iv = iv;
            this.serialNumber = serialNumber;
            this.macAddress = macAddress;
        }

        @Override
        public byte[] asByteArray() {
            return Command.serialize(
                encrypted,
                ":",
                iv,
                ":",
                COMMAND,
                ":",
                StringUtils.leftPad(StringUtils.lowerCase(HexUtil.asString(serialNumber)), 8, '0'),
                ":",
                macAddress
            );
        }

        public byte[] getEncrypted() {
            return encrypted;
        }

        public byte[] getIv() {
            return iv;
        }

        public Long getSerialNumber() {
            return serialNumber;
        }

        public String getMacAddress() {
            return macAddress;
        }
    }
}
