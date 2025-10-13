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

package pl.psobiech.opengr8on.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;

/**
 * Common random operations
 */
public final class RandomUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(RandomUtil.class);

    public static final SecureRandom RANDOM = new SecureRandom();

    private static final int BYTE_MASK = 0xFF;

    private static final int NIBBLE_MASK = 0x0F;

    private static final char[] HEX_DICTIONARY;

    static {
        final char[] chars = new char[HexUtil.HEX_BASE];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = Integer.toHexString(i).charAt(0);
        }

        HEX_DICTIONARY = chars;
    }

    private RandomUtil() {
        // NOP
    }

    /**
     * @return random hex string
     */
    public static String hexString(int length) {
        return dictionaryString(length, HEX_DICTIONARY);
    }

    /**
     * @return random dictionary string
     */
    private static String dictionaryString(int length, char[] dictionary) {
        final StringBuilder sb = new StringBuilder();

        final byte[] randomBytes = bytes(Math.floorDiv(length + 1, 2));
        for (byte randomByte : randomBytes) {
            final int unsignedByte = randomByte & BYTE_MASK;

            sb.append(dictionary[unsignedByte & NIBBLE_MASK]);
            sb.append(dictionary[unsignedByte >> (Byte.SIZE / 2)]);
        }

        if (sb.length() > length) {
            sb.setLength(length);
        }

        return sb.toString();
    }

    /**
     * @return random array of bytes
     */
    public static byte[] bytes(int size) {
        final byte[] salt = new byte[size];
        RANDOM.nextBytes(salt);

        return salt;
    }

    /**
     * @return random int (lower than max)
     */
    public static int integer(int maxExclusive) {
        return RANDOM.nextInt(maxExclusive);
    }

    /**
     * @return random int
     */
    public static int integer() {
        return RANDOM.nextInt();
    }

    /**
     * @return random int
     */
    public static long longInteger() {
        return RANDOM.nextLong();
    }
}
