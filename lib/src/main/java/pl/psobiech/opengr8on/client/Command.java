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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import pl.psobiech.opengr8on.exceptions.UnexpectedException;

public interface Command {
    int INITIAL_BUFFER_SIZE = 256;

    int MIN_IP_SIZE = 7;

    int IV_SIZE = 16;

    int KEY_SIZE = 16;

    int MAC_SIZE = 12;

    int MIN_SERIAL_NUMBER_SIZE = 4;

    int MIN_SESSION_SIZE = 6;

    int RANDOM_SIZE = 30;

    int RANDOM_ENCRYPTED_SIZE = 32;

    static boolean equals(String value1, byte[] buffer, int offset) {
        if (value1 == null) {
            return false;
        }

        return value1.equals(asString(buffer, offset, value1.length()));
    }

    static String asString(byte[] buffer) {
        return asString(buffer, 0);
    }

    static String asString(byte[] buffer, int offset) {
        return asStringOfRange(buffer, offset, buffer.length);
    }

    static String asString(byte[] buffer, int offset, int limit) {
        return asStringOfRange(buffer, offset, offset + limit);
    }

    private static String asStringOfRange(byte[] buffer, int from, int to) {
        return new String(Arrays.copyOfRange(buffer, from, to)).trim();
    }

    default String uuid(UUID uuid) {
        return Client.uuid(uuid, this);
    }

    static byte[] serialize(Object... objects) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(INITIAL_BUFFER_SIZE)) {
            for (Object object : objects) {
                outputStream.write(serializeObject(object));
            }

            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    private static byte[] serializeObject(Object object) {
        if (object instanceof byte[]) {
            return (byte[]) object;
        }

        if (object instanceof String) {
            return ((String) object).getBytes(StandardCharsets.UTF_8);
        }

        if (object instanceof Long || object instanceof Integer) {
            return serializeObject(String.valueOf(object));
        }

        if (object instanceof Inet4Address) {
            return serializeObject(((Inet4Address) object).getHostAddress());
        }

        throw new UnexpectedException("Unsupported object type: " + object);
    }

    byte[] asByteArray();
}
