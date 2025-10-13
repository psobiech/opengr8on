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

import pl.psobiech.opengr8on.client.Command;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.Util;

import java.net.Inet4Address;
import java.util.Optional;

public class ResetCommand {
    private ResetCommand() {
        // NOP
    }

    public static Request request(Inet4Address ipAddress) {
        return new Request(ipAddress);
    }

    public static Optional<Request> requestFromByteArray(byte[] buffer) {
        if (!requestMatches(buffer)) {
            return Optional.empty();
        }

        final Optional<String[]> requestPartsOptional = Util.splitExact(Command.asString(buffer), ":", 2);
        if (requestPartsOptional.isEmpty()) {
            return Optional.empty();
        }

        final String[] requestParts = requestPartsOptional.get();
        final Inet4Address ipAddress = IPv4AddressUtil.parseIPv4(requestParts[1]);

        return Optional.of(
                new Request(
                        ipAddress
                )
        );
    }

    public static boolean requestMatches(byte[] buffer) {
        if (buffer.length < Request.COMMAND.length() + 1 + Command.MIN_IP_CHARACTERS) {
            return false;
        }

        if (buffer[Request.COMMAND.length()] != ':') {
            return false;
        }

        return Request.COMMAND.equals(
                Command.asString(buffer, 0, Request.COMMAND.length())
        );
    }

    public static Response response(Inet4Address ipAddress) {
        return new Response(ipAddress);
    }

    public static Optional<Response> responseFromByteArray(byte[] buffer) {
        if (!responseMatches(buffer)) {
            return Optional.empty();
        }

        final Optional<String[]> responsePartsOptional = Util.splitExact(Command.asString(buffer), ":", 2);
        if (responsePartsOptional.isEmpty()) {
            return Optional.empty();
        }

        final String[] responseParts = responsePartsOptional.get();
        final Inet4Address localAddress = IPv4AddressUtil.parseIPv4(responseParts[1]);

        return Optional.of(
                new Response(
                        localAddress
                )
        );
    }

    public static boolean responseMatches(byte[] buffer) {
        if (buffer.length < Response.COMMAND.length() + 1 + Command.MIN_IP_CHARACTERS) {
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
        protected static final String COMMAND = "req_reset";

        private final Inet4Address ipAddress;

        private Request(Inet4Address ipAddress) {
            this.ipAddress = ipAddress;
        }

        @Override
        public byte[] asByteArray() {
            return Command.serialize(
                    COMMAND,
                    ":",
                    ipAddress
            );
        }

        public Inet4Address getIpAddress() {
            return ipAddress;
        }
    }

    public static class Response implements Command {
        protected static final String COMMAND = "resp_reset";

        private final Inet4Address ipAddress;

        private Response(Inet4Address ipAddress) {
            this.ipAddress = ipAddress;
        }

        @Override
        public byte[] asByteArray() {
            return Command.serialize(
                    COMMAND,
                    ":",
                    ipAddress
            );
        }

        public Inet4Address getIpAddress() {
            return ipAddress;
        }
    }
}
