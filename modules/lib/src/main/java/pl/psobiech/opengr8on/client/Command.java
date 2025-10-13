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

package pl.psobiech.opengr8on.client;

import pl.psobiech.opengr8on.exceptions.UnexpectedException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

public interface Command {
    /**
     * Initial buffer size to allocate for commands
     */
    int INITIAL_BUFFER_BYTES = 256;

    /**
     * Minimum size of IPv4 address, e.g. "0.0.0.0".length
     */
    int MIN_IP_CHARACTERS = 7;

    int IV_BYTES = 16;

    int KEY_BYTES = 16;

    /**
     * Mac address size, without :, e.g. 000000000000
     */
    int MAC_CHARACTERS = 12;

    int MIN_SERIAL_NUMBER_CHARACTERS = 4;

    int MAX_SERIAL_NUMBER_CHARACTERS = 8;

    int PIN_CHARACTERS = 8;

    int MIN_SESSION_CHARACTERS = 6;

    int MAX_SESSION_CHARACTERS = 8;

    int RANDOM_BYTES = 30;

    int RANDOM_ENCRYPTED_BYTES = 32;

    static boolean equals(String value1, byte[] buffer, int offset) {
        if (value1 == null) {
            return false;
        }

        return value1.equals(asString(buffer, offset, value1.length()));
    }

    /**
     * @return whole buffer as String
     */
    static String asString(byte[] buffer) {
        return asString(buffer, 0);
    }

    /**
     * @return rest of the buffer from offset as String
     */
    static String asString(byte[] buffer, int offset) {
        return asStringOfRange(buffer, offset, buffer.length);
    }

    /**
     * @return String, between offset and limit in the buffer
     */
    static String asString(byte[] buffer, int offset, int limit) {
        return asStringOfRange(buffer, offset, offset + limit);
    }

    private static String asStringOfRange(byte[] buffer, int from, int to) {
        return new String(Arrays.copyOfRange(buffer, from, to)).trim();
    }

    /**
     * @return objects serialized as byte array
     */
    static byte[] serialize(Object... objects) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(INITIAL_BUFFER_BYTES)) {
            for (Object object : objects) {
                outputStream.write(serializeObject(object));
            }

            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    /**
     * @return object serialized as byte array
     */
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

    /**
     * @return Command unique UUID (to correlate request / responses)
     */
    default String uuid(UUID uuid) {
        return Client.uuid(uuid, this);
    }

    /**
     * @return command as byte array
     */
    byte[] asByteArray();
}
