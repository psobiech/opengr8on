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

package pl.psobiech.opengr8on.client.commands;

import java.net.Inet4Address;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import pl.psobiech.opengr8on.client.Command;
import pl.psobiech.opengr8on.util.HexUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.Util;

public class SetIpCommand {
    private static final int SERIAL_NUMBER_PART = 1;

    private static final int IP_ADDRESS_PART = 2;

    private static final int GATEWAY_IP_ADDRESS_PART = 3;

    private SetIpCommand() {
        // NOP
    }

    public static Request request(Long serialNumber, Inet4Address ipAddress, Inet4Address gatewayIpAddress) {
        return new Request(serialNumber, ipAddress, gatewayIpAddress);
    }

    public static Optional<Request> requestFromByteArray(byte[] buffer) {
        if (!requestMatches(buffer)) {
            return Optional.empty();
        }

        final Optional<String[]> requestPartsOptional = Util.splitExact(Command.asString(buffer), ":", 4);
        if (requestPartsOptional.isEmpty()) {
            return Optional.empty();
        }

        final String[] requestParts = requestPartsOptional.get();
        final Long serialNumber = HexUtil.asLong(requestParts[SERIAL_NUMBER_PART]);
        final Inet4Address ipAddress = IPv4AddressUtil.parseIPv4(requestParts[IP_ADDRESS_PART]);
        final Inet4Address gatewayIpAddress = IPv4AddressUtil.parseIPv4(requestParts[GATEWAY_IP_ADDRESS_PART]);

        return Optional.of(
                new Request(
                        serialNumber,
                        ipAddress, gatewayIpAddress
                )
        );
    }

    public static boolean requestMatches(byte[] buffer) {
        if (buffer.length < Request.COMMAND.length() + 1 + Command.MIN_SERIAL_NUMBER_CHARACTERS + 1 + Command.MIN_IP_CHARACTERS + 1 + Command.MIN_IP_CHARACTERS) {
            return false;
        }

        if (buffer[Request.COMMAND.length()] != ':') {
            return false;
        }

        return Request.COMMAND.equals(
                Command.asString(buffer, 0, Request.COMMAND.length())
        );
    }

    public static Response response(Long serialNumber, Inet4Address ipAddress) {
        return new Response(serialNumber, ipAddress);
    }

    public static Optional<Response> responseFromByteArray(byte[] buffer) {
        if (!responseMatches(buffer)) {
            return Optional.empty();
        }

        final Optional<String[]> responsePartsOptional = Util.splitExact(Command.asString(buffer), ":", 3);
        if (responsePartsOptional.isEmpty()) {
            return Optional.empty();
        }

        final String[] responseParts = responsePartsOptional.get();
        final Long serialNumber = HexUtil.asLong(responseParts[1]);
        final Inet4Address ipAddress = IPv4AddressUtil.parseIPv4(responseParts[2]);

        return Optional.of(
                new Response(
                        serialNumber,
                        ipAddress
                )
        );
    }

    public static boolean responseMatches(byte[] buffer) {
        if (buffer.length < Response.COMMAND.length() + 1 + Command.MIN_SERIAL_NUMBER_CHARACTERS + 1 + Command.MIN_IP_CHARACTERS) {
            return false;
        }

        if (buffer[Response.COMMAND.length()] != ':') {
            return false;
        }

        return Response.COMMAND.equals(
                Command.asString(buffer, 0, Response.COMMAND.length())
        );
    }

    public static class Request implements Command {
        protected static final String COMMAND = "req_set_clu_ip";

        private final Long serialNumber;

        private final Inet4Address ipAddress;

        private final Inet4Address gatewayIpAddress;

        private Request(Long serialNumber, Inet4Address ipAddress, Inet4Address gatewayIpAddress) {
            this.serialNumber = serialNumber;
            this.ipAddress = ipAddress;
            this.gatewayIpAddress = gatewayIpAddress;
        }

        @Override
        public byte[] asByteArray() {
            return Command.serialize(
                    COMMAND,
                    ":",
                    StringUtils.lowerCase(StringUtils.leftPad(HexUtil.asString(serialNumber), MAX_SERIAL_NUMBER_CHARACTERS, '0')),
                    ":",
                    ipAddress,
                    ":",
                    gatewayIpAddress
            );
        }

        public Long getSerialNumber() {
            return serialNumber;
        }

        public Inet4Address getIpAddress() {
            return ipAddress;
        }

        public Inet4Address getGatewayIpAddress() {
            return gatewayIpAddress;
        }
    }

    public static class Response implements Command {
        protected static final String COMMAND = "resp_set_clu_ip";

        private final Long serialNumber;

        private final Inet4Address ipAddress;

        private Response(Long serialNumber, Inet4Address ipAddress) {
            this.serialNumber = serialNumber;
            this.ipAddress = ipAddress;
        }

        @Override
        public byte[] asByteArray() {
            return Command.serialize(
                    COMMAND,
                    ":",
                    StringUtils.leftPad(StringUtils.lowerCase(HexUtil.asString(serialNumber)), MAX_SERIAL_NUMBER_CHARACTERS, '0'),
                    ":",
                    ipAddress
            );
        }

        public Long getSerialNumber() {
            return serialNumber;
        }

        public Inet4Address getIpAddress() {
            return ipAddress;
        }
    }
}
