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

import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;

import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.RandomUtil;

public class Mocks {
    private Mocks() {
        // NOP
    }

    public static CipherKey cipherKey() {
        return new CipherKey(key(), iv());
    }

    public static byte[] key() {
        return RandomUtil.bytes(16);
    }

    public static byte[] iv() {
        return RandomUtil.bytes(16);
    }

    public static Inet4Address localIpAddress() {
        final int ipAsNumber = IPv4AddressUtil.getIPv4AsNumber("127.0.0.1");

        return IPv4AddressUtil.parseIPv4(
            ipAsNumber + RandomUtil.random(false).nextInt(255)
        );
    }

    public static Inet4Address ipAddress() {
        final int ipAsNumber = IPv4AddressUtil.getIPv4AsNumber("192.168.31.1");

        return IPv4AddressUtil.parseIPv4(
            ipAsNumber + RandomUtil.random(false).nextInt(255)
        );
    }

    public static long serialNumber() {
        return RandomUtil.longInteger();
    }

    public static int sessionId() {
        return RandomUtil.integer();
    }

    public static String macAddress() {
        return RandomUtil.hexString(12);
    }

    public static byte[] pin() {
        return RandomUtil.hexString(8).getBytes(StandardCharsets.US_ASCII);
    }
}
