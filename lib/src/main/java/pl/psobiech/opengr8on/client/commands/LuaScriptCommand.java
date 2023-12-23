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
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import pl.psobiech.opengr8on.client.Command;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.SocketUtil.Payload;
import pl.psobiech.opengr8on.util.HexUtil;

public class LuaScriptCommand {
    public static final String CHECK_ALIVE = "checkAlive()";

    private LuaScriptCommand() {
        // NOP
    }

    public static Request request(Inet4Address ipAddress, Integer sessionId, String script) {
        return new Request(
            ipAddress, sessionId, script
        );
    }

    public static Optional<Request> requestFromByteArray(byte[] buffer) {
        if (!requestMatches(buffer)) {
            return Optional.empty();
        }

        final String[] requestParts = Command.asString(buffer).split(":", 4);
        final Inet4Address ipAddress = IPv4AddressUtil.parseIPv4(requestParts[1]);
        final Integer sessionId = HexUtil.asInt(requestParts[2]);
        final String script = requestParts[3];

        return Optional.of(
            new Request(
                ipAddress,
                sessionId,
                script
            )
        );
    }

    public static boolean requestMatches(byte[] buffer) {
        if (buffer.length < Request.COMMAND.length() + 1 + Command.MIN_IP_SIZE + 1 + Command.MIN_SESSION_SIZE + 1 + 1 /* script */) {
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

    public static Optional<String> parse(Integer sessionId, Payload payload) {
        final Optional<Response> responseOptional = responseFromByteArray(payload.buffer());
        if (responseOptional.isEmpty()) {
            return Optional.empty();
        }

        final Response response = responseOptional.get();
        if (!sessionId.equals(response.sessionId)) {
            return Optional.empty();
        }

        return Optional.of(
            responseOptional.get().returnValue
        );
    }

    public static Optional<Response> responseFromByteArray(byte[] buffer) {
        if (!responseMatches(buffer)) {
            return Optional.empty();
        }

        final String[] responseParts = Command.asString(buffer).split(":", 4);
        final Inet4Address ipAddress = IPv4AddressUtil.parseIPv4(responseParts[1]);
        final Integer sessionId = HexUtil.asInt(responseParts[2]);
        final String returnValue = responseParts[3];

        return Optional.of(
            new Response(
                ipAddress,
                sessionId,
                returnValue
            )
        );
    }

    public static boolean responseMatches(byte[] buffer) {
        if (buffer.length < Response.COMMAND.length() + 1 + Command.MIN_IP_SIZE + 1 + Command.MIN_SESSION_SIZE + 1 + 1 /* script */) {
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
        static final String COMMAND = "req";

        private final Inet4Address ipAddress;

        private final Integer sessionId;

        private final String script;

        private Request(Inet4Address ipAddress, Integer sessionId, String script) {
            this.ipAddress = ipAddress;
            this.sessionId = sessionId;
            this.script = script;
        }

        @Override
        public byte[] asByteArray() {
            return Command.serialize(
                COMMAND,
                ":",
                ipAddress,
                ":",
                StringUtils.leftPad(StringUtils.lowerCase(HexUtil.asString(sessionId)), 8, '0'),
                ":",
                script + "\r\n"
            );
        }

        public Inet4Address getIpAddress() {
            return ipAddress;
        }

        public Integer getSessionId() {
            return sessionId;
        }

        public String getScript() {
            return script;
        }
    }

    public static class Response implements Command {
        static final String COMMAND = "resp";

        private final Inet4Address ipAddress;

        private final Integer sessionId;

        private final String returnValue;

        private Response(Inet4Address ipAddress, Integer sessionId, String returnValue) {
            this.ipAddress = ipAddress;
            this.sessionId = sessionId;
            this.returnValue = returnValue;
        }

        @Override
        public byte[] asByteArray() {
            return Command.serialize(
                COMMAND,
                ":",
                ipAddress,
                ":",
                StringUtils.leftPad(StringUtils.lowerCase(HexUtil.asString(sessionId)), 8, '0'),
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
