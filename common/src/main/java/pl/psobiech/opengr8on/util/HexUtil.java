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

import java.math.BigInteger;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;

import static org.apache.commons.lang3.StringUtils.upperCase;

/**
 * Common hexadecimal operations
 */
public final class HexUtil {
    static final int HEX_BASE = 16;

    static final String HEX_PREFIX = "0x";

    private HexUtil() {
        // NOP
    }

    /**
     * @return parses hex string to long (strips 0x if present)
     */
    public static long asLong(String hexAsString) {
        try {
            return Long.parseUnsignedLong(
                stripPrefix(hexAsString),
                HEX_BASE
            );
        } catch (NumberFormatException e) {
            throw new UnexpectedException(String.format("Value %s is not in the correct HEX format", hexAsString), e);
        }
    }

    /**
     * @return parses hex string to integer (strips 0x if present)
     */
    public static int asInt(String hexAsString) {
        try {
            return Integer.parseUnsignedInt(
                stripPrefix(hexAsString),
                HEX_BASE
            );
        } catch (NumberFormatException e) {
            throw new UnexpectedException(String.format("Value %s is not in the correct HEX format", hexAsString), e);
        }
    }

    /**
     * @return parses hex string to raw byte array (strips 0x if present)
     */
    public static byte[] asBytes(String hexAsString) {
        try {
            return Hex.decodeHex(
                stripPrefix(hexAsString)
            );
        } catch (DecoderException e) {
            throw new UnexpectedException(String.format("Value %s is not in the correct HEX format", hexAsString), e);
        }
    }

    /**
     * @return string without 0x prefix, if present
     */
    private static String stripPrefix(String hexAsString) {
        if (hexAsString.startsWith(HEX_PREFIX)) {
            return hexAsString.substring(HEX_PREFIX.length());
        }

        return hexAsString;
    }

    /**
     * @return hex value as string
     */
    public static String asString(BigInteger value) {
        if (value == null) {
            return null;
        }

        return format((value).toString(HEX_BASE));
    }

    /**
     * @return hex value as string
     */
    public static String asString(byte value) {
        return asString(value & 0xFF);
    }

    /**
     * @return hex value as string
     */
    public static String asString(byte[] array) {
        return format(Hex.encodeHexString(array));
    }

    /**
     * @return hex value as string
     */
    public static String asString(Integer value) {
        if (value == null) {
            return null;
        }

        return format(Integer.toHexString(value));
    }

    /**
     * @return hex value as string
     */
    public static String asString(Long value) {
        if (value == null) {
            return null;
        }

        return format(Long.toHexString(value));
    }

    /**
     * @return hex formatted (uppercase and zero padded to even characters)
     */
    private static String format(String valueAsString) {
        return evenZeroLeftPad(
            upperCase(valueAsString)
        );
    }

    /**
     * @return even zero padded hex string, e.g. 0 = 00
     */
    private static String evenZeroLeftPad(String valueAsString) {
        if (valueAsString.length() % 2 == 0) {
            return valueAsString;
        }

        return '0' + valueAsString;
    }
}
