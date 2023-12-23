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

package pl.psobiech.opengr8on.util;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;

public final class RandomUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(RandomUtil.class);

    private static final ThreadLocal<SecureRandom> WEAK_RANDOM_THREAD_LOCAL = ThreadLocal.withInitial(RandomUtil::createWeakRandom);

    private static final SecureRandom STRONG_RANDOM;

    private static final int UNIQUE_MAX_RETRIES = 32;

    private static final int BYTE_MASK = 0xFF;

    private static final int NIBBLE_MASK = 0x0F;

    private static final char[] HEX_DICTIONARY;

    static {
        try {
            STRONG_RANDOM = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            throw new UnexpectedException("No strong random PRNG available", e);
        }

        final char[] chars = new char[HexUtil.HEX_BASE];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = Integer.toHexString(i).charAt(0);
        }

        HEX_DICTIONARY = chars;
    }

    private static SecureRandom createWeakRandom() {
        return new SecureRandom();
    }

    private RandomUtil() {
        // NOP
    }

    public static String unique(int length, Function<Integer, String> generatorFunction, Function<String, Boolean> existsFunction) {
        int retries = UNIQUE_MAX_RETRIES;

        String candidate;
        do {
            if (--retries < 0) {
                throw new UnexpectedException("Cannot generate unique value");
            }

            candidate = generatorFunction.apply(length);
        } while (existsFunction.apply(candidate) && !Thread.interrupted());

        return candidate;
    }

    /**
     * @return random hex string (weak rng)
     */
    public static String hexString(int length) {
        return dictionaryString(random(false), length, HEX_DICTIONARY);
    }

    /**
     * @return random hex string
     */
    public static String hexString(Random random, int length) {
        return dictionaryString(random, length, HEX_DICTIONARY);
    }

    /**
     * @return random dictionary string
     */
    private static String dictionaryString(Random random, int length, char[] dictionary) {
        final StringBuilder sb = new StringBuilder();

        final byte[] randomBytes = bytes(random, Math.floorDiv(length + 1, 2));
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
     * @return random array of bytes (weak rng)
     */
    public static byte[] bytes(int size) {
        return bytes(random(false), size);
    }

    /**
     * @return random array of bytes
     */
    public static byte[] bytes(Random random, int size) {
        final byte[] salt = new byte[size];
        random.nextBytes(salt);

        return salt;
    }

    public static SecureRandom random(boolean strong) {
        if (strong) {
            return RandomUtil.STRONG_RANDOM;
        }

        return RandomUtil.WEAK_RANDOM_THREAD_LOCAL.get();
    }
}
