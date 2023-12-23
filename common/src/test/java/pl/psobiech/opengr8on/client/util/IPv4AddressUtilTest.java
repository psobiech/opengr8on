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

package pl.psobiech.opengr8on.client.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Collection;

import org.junit.jupiter.api.Test;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil.NetworkInterfaceDto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IPv4AddressUtilTest {
    @Test
    void testParseIPv4() throws Exception {
        final InetAddress expected = InetAddress.getByName("192.168.31.31");

        final Inet4Address actual = IPv4AddressUtil.parseIPv4("192.168.31.31");

        assertEquals(expected, actual);
    }

    @Test
    void testParseIPv4AsNumber() throws Exception {
        final int expected = 0xA489001;

        final int actual = IPv4AddressUtil.getIPv4AsNumber("10.72.144.1");

        assertEquals(expected, actual);
    }

    @Test
    void testGetIPv4FromNumber() throws Exception {
        final String expected = "10.72.144.1";

        final String actual = IPv4AddressUtil.getIPv4FromNumber(0xA489001);

        assertEquals(expected, actual);
    }

    @Test
    void testGetNetworkInterfaces() throws Exception {
        final Collection<NetworkInterfaceDto> networkInterfaces = IPv4AddressUtil.getLocalIPv4NetworkInterfaces();

        assertFalse(networkInterfaces.isEmpty());
    }
}