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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Common toString() operations
 */
public final class ToStringUtil {
    private ToStringUtil() {
        // NOP
    }

    /**
     * @return IPv4 and port as String
     */
    public static String toString(Inet4Address address, Integer port) {
        final String addressAsString = toString(address);
        if (port == null) {
            return addressAsString;
        }

        return "%s:%d".formatted(addressAsString, port);
    }

    /**
     * @return IPv4 as String
     */
    public static String toString(Inet4Address address) {
        if (address == null) {
            return null;
        }

        return "%s::%s%s".formatted(
                address.getHostAddress(),
                HexUtil.HEX_PREFIX, HexUtil.asString(IPv4AddressUtil.getIPv4AsNumber(address))
        );
    }

    /**
     * @return Long as String
     */
    public static String toString(Long value) {
        if (value == null) {
            return null;
        }

        return "%d::%s%s".formatted(
                value,
                HexUtil.HEX_PREFIX, HexUtil.asString(value)
        );
    }

    /**
     * @return Integer as String
     */
    public static String toString(Integer value) {
        if (value == null) {
            return null;
        }

        return "%d::%s%s".formatted(
                value,
                HexUtil.HEX_PREFIX, HexUtil.asString(value)
        );
    }

    /**
     * @return NetworkInterface as String
     */
    public static String toString(NetworkInterface networkInterface) {
        if (networkInterface == null) {
            return null;
        }

        byte[] hardwareAddress = null;
        try {
            hardwareAddress = networkInterface.getHardwareAddress();
        } catch (SocketException e) {
            // NOP
        }

        return networkInterface.getName() + " (" + networkInterface.getDisplayName() + ") [" + toString(hardwareAddress) + "]";
    }

    /**
     * @return byte buffer as String
     */
    public static String toString(byte[] buffer) {
        if (buffer == null) {
            return null;
        }

        final String asciiValues = new String(buffer)
                .replaceAll("[^\\p{Graph} ]", ".");

        final String hexString = StringUtils.stripToEmpty(
                IntStream.range(0, buffer.length)
                         .mapToObj(i -> (i % 4 == 0 ? " " : "") + HexUtil.asString(buffer[i]))
                         .collect(Collectors.joining())
        );

        return "'%s # %s # %s'".formatted(
                asciiValues,
                hexString,
                Base64.encodeBase64String(buffer)
        );
    }
}
