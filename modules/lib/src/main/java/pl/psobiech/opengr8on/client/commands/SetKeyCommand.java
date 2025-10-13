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

import java.util.Arrays;
import java.util.Optional;

public class SetKeyCommand {
    private static final Response RESPONSE = new Response();

    private SetKeyCommand() {
        // NOP
    }

    public static Request request(byte[] encrypted, byte[] key, byte[] iv) {
        return new Request(encrypted, key, iv);
    }

    public static Optional<Request> requestFromByteArray(byte[] buffer) {
        if (!requestMatches(buffer)) {
            return Optional.empty();
        }

        final byte[] encrypted = Arrays.copyOf(buffer, Command.RANDOM_ENCRYPTED_BYTES);
        final byte[] iv = Arrays.copyOfRange(buffer, Command.RANDOM_ENCRYPTED_BYTES + 1, Command.RANDOM_ENCRYPTED_BYTES + 1 + Command.KEY_BYTES);
        final byte[] key = Arrays.copyOfRange(
                buffer,
                Command.RANDOM_ENCRYPTED_BYTES + 1 + Command.IV_BYTES + 1 + Request.COMMAND.length() + 1,
                Command.RANDOM_ENCRYPTED_BYTES + 1 + Command.IV_BYTES + 1 + Request.COMMAND.length() + 1 + Command.KEY_BYTES
        );

        return Optional.of(
                new Request(
                        encrypted,
                        key, iv
                )
        );
    }

    public static boolean requestMatches(byte[] buffer) {
        if (
                buffer.length != Command.RANDOM_ENCRYPTED_BYTES + 1 + Command.IV_BYTES + 1 + Request.COMMAND.length() + 1 + Command.KEY_BYTES
                        && buffer.length != Command.RANDOM_ENCRYPTED_BYTES + 1 + Command.IV_BYTES + 1 + Request.COMMAND.length() + 1 + Command.KEY_BYTES + FileUtil.CRLF.length()
        ) {
            return false;
        }

        if (buffer[Command.RANDOM_ENCRYPTED_BYTES] != ':'
                || buffer[Command.RANDOM_ENCRYPTED_BYTES + 1 + Command.IV_BYTES] != ':'
                || buffer[Command.RANDOM_ENCRYPTED_BYTES + 1 + Command.IV_BYTES + 1 + Request.COMMAND.length()] != ':'
        ) {
            return false;
        }

        return Request.COMMAND.equals(
                Command.asString(
                        buffer,
                        Command.RANDOM_ENCRYPTED_BYTES + 1 + Command.IV_BYTES + 1,
                        Request.COMMAND.length()
                )
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
        static final String COMMAND = "req_set_key";

        private final byte[] encrypted;

        private final byte[] key;

        private final byte[] iv;

        private Request(byte[] encrypted, byte[] key, byte[] iv) {
            this.encrypted = encrypted;
            this.key = key;
            this.iv = iv;
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
                    key
            );
        }

        public byte[] getEncrypted() {
            return encrypted;
        }

        public byte[] getKey() {
            return key;
        }

        public byte[] getIV() {
            return iv;
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
