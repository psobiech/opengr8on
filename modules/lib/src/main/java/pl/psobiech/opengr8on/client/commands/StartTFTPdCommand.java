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
import pl.psobiech.opengr8on.util.FileUtil;

import java.util.Optional;

public class StartTFTPdCommand {
    private static final Request REQUEST = new Request();

    private static final Response RESPONSE = new Response();

    private StartTFTPdCommand() {
        // NOP
    }

    public static Request request() {
        return REQUEST;
    }

    public static Optional<Request> requestFromByteArray(byte[] buffer) {
        if (!requestMatches(buffer)) {
            return Optional.empty();
        }

        return Optional.of(REQUEST);
    }

    public static boolean requestMatches(byte[] buffer) {
        if (buffer.length != Request.COMMAND.length()
                && buffer.length != Request.COMMAND.length() + FileUtil.CRLF.length()) {
            return false;
        }

        return Request.COMMAND.equals(
                Command.asString(buffer, 0, Request.COMMAND.length())
        );
    }

    public static Response response() {
        return RESPONSE;
    }

    public static Optional<Response> responseFromByteArray(byte[] buffer) {
        if (!responseMatches(buffer)) {
            return Optional.empty();
        }

        return Optional.of(RESPONSE);
    }

    public static boolean responseMatches(byte[] buffer) {
        if (buffer.length != Response.COMMAND.length()
                && buffer.length != Response.COMMAND.length() + FileUtil.CRLF.length()) {
            return false;
        }

        return Response.COMMAND.equals(
                Command.asString(buffer, 0, Response.COMMAND.length())
        );
    }

    public static class Request implements Command {
        static final String COMMAND = "req_start_ftp";

        private Request() {
            // NOP
        }

        @Override
        public byte[] asByteArray() {
            return Command.serialize(COMMAND);
        }
    }

    public static class Response implements Command {
        static final String COMMAND = "resp:OK";

        private Response() {
            // NOP
        }

        @Override
        public byte[] asByteArray() {
            return Command.serialize(
                    COMMAND
            );
        }
    }
}
