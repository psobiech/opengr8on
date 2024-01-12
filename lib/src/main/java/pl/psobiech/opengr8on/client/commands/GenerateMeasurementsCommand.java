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

public class GenerateMeasurementsCommand {
    private static final int IP_ADDRESS_PART = 1;

    private static final int SESSION_ID_PART = 2;

    private static final int UNKNOWN_PART = 3;

    private static final int RETURN_VALUE_PART = 3;

    private GenerateMeasurementsCommand() {
        // NOP
    }

    public static Request request(Inet4Address ipAddress, Integer sessionId, String unknown) {
        return new Request(
            ipAddress,
            sessionId,
            unknown
        );
    }

    public static Optional<Request> requestFromByteArray(byte[] buffer) {
        if (!requestMatches(buffer)) {
            return Optional.empty();
        }

        final String[] requestParts = Command.asString(buffer).split(":", 4);
        final Inet4Address ipAddress = IPv4AddressUtil.parseIPv4(requestParts[IP_ADDRESS_PART]);
        final Integer sessionId = HexUtil.asInt(requestParts[SESSION_ID_PART]);
        final String unknown = requestParts[UNKNOWN_PART];

        return Optional.of(
            new Request(
                ipAddress,
                sessionId,
                unknown
            )
        );
    }

    public static boolean requestMatches(byte[] buffer) {
        if (buffer.length < Request.COMMAND.length() + 1 + Command.MIN_IP_CHARACTERS + 1 + Command.MIN_SESSION_CHARACTERS + 1 + 1 /* script */) {
            return false;
        }

        if (buffer[Request.COMMAND.length()] != ':') {
            return false;
        }

        return Request.COMMAND.equals(
            Command.asString(buffer, 0, Request.COMMAND.length())
        );
    }

    public static Response response(Inet4Address ipAddress, int sessionId, String returnValue) {
        return new Response(
            ipAddress, sessionId, returnValue
        );
    }

    public static Optional<Response> responseFromByteArray(byte[] buffer) {
        if (!responseMatches(buffer)) {
            return Optional.empty();
        }

        final String[] responseParts = Command.asString(buffer).split(":", 4);
        final Inet4Address ipAddress = IPv4AddressUtil.parseIPv4(responseParts[IP_ADDRESS_PART]);
        final Integer sessionId = HexUtil.asInt(responseParts[SESSION_ID_PART]);
        final String returnValue = responseParts[RETURN_VALUE_PART];

        return Optional.of(
            new Response(
                ipAddress,
                sessionId,
                returnValue
            )
        );
    }

    public static boolean responseMatches(byte[] buffer) {
        if (buffer.length < Response.COMMAND.length() + 1 + Command.MIN_IP_CHARACTERS + 1 + Command.MIN_SESSION_CHARACTERS + 1 + 1 /* returnValue */) {
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
        static final String COMMAND = "req_gen_measurements";

        private final Inet4Address ipAddress;

        private final Integer sessionId;

        private final String unknown;

        private Request(Inet4Address ipAddress, Integer sessionId, String unknown) {
            this.ipAddress = ipAddress;
            this.sessionId = sessionId;
            this.unknown   = unknown;
        }

        @Override
        public byte[] asByteArray() {
            return Command.serialize(
                COMMAND,
                ":",
                ipAddress,
                ":",
                StringUtils.leftPad(StringUtils.lowerCase(HexUtil.asString(sessionId)), Command.MAX_SESSION_CHARACTERS, '0'),
                ":",
                unknown + "\r\n"
            );
        }

        public Inet4Address getIpAddress() {
            return ipAddress;
        }

        public Integer getSessionId() {
            return sessionId;
        }

        public String getUnknown() {
            return unknown;
        }
    }

    public static class Response implements Command {
        static final String COMMAND = "meas_file_download";

        private final Inet4Address ipAddress;

        private final Integer sessionId;

        private final String returnValue;

        private Response(Inet4Address ipAddress, Integer sessionId, String returnValue) {
            this.ipAddress   = ipAddress;
            this.sessionId   = sessionId;
            this.returnValue = returnValue;
        }

        @Override
        public byte[] asByteArray() {
            return Command.serialize(
                COMMAND,
                ":",
                ipAddress,
                ":",
                StringUtils.leftPad(StringUtils.lowerCase(HexUtil.asString(sessionId)), MAX_SESSION_CHARACTERS, '0'),
                ":",
                returnValue
            );
        }

        public Inet4Address getIpAddress() {
            return ipAddress;
        }

        public Integer getSessionId() {
            return sessionId;
        }

        public String getReturnValue() {
            return returnValue;
        }
    }
}
