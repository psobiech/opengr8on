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

import java.util.Optional;

import pl.psobiech.opengr8on.client.Command;

public class ErrorCommand {
    private static final Response RESPONSE = new Response();

    private ErrorCommand() {
        // NOP
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
            && buffer.length != Response.COMMAND.length() + 2 /* \r\n */) {
            return false;
        }

        return Response.COMMAND.equals(
            Command.asString(buffer, 0, Response.COMMAND.length())
        );
    }

    public static class Response implements Command {
        static final String COMMAND = "resp:ERROR";

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
