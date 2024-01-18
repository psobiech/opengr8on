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

package pl.psobiech.opengr8on.vclu;

import java.net.Inet4Address;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.psobiech.opengr8on.client.CLUClient;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.Mocks;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.client.device.CipherTypeEnum;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.psobiech.opengr8on.vclu.MockServer.LOCALHOST;

class ServerCommandTest extends BaseServerTest {
    private static MockServer server;

    private static CipherKey projectCipherKey;

    @BeforeAll
    static void setUp() throws Exception {
        final long serialNumber = Mocks.serialNumber();
        projectCipherKey = Mocks.cipherKey();

        server = new MockServer(
            projectCipherKey,
            new CLUDevice(
                serialNumber,
                Mocks.macAddress(),
                MockServer.LOCALHOST,
                CipherTypeEnum.PROJECT,
                Mocks.iv(),
                Mocks.pin()
            )
        );

        server.start();
    }

    @AfterAll
    static void tearDown() throws Exception {
        IOUtil.closeQuietly(server);
    }

    @Test
    void checkAlive() throws Exception {
        try (CLUClient client = new CLUClient(LOCALHOST, server.getServer().getDevice(), projectCipherKey, LOCALHOST, server.getPort())) {
            final Optional<Boolean> aliveOptional = client.checkAlive();

            assertTrue(aliveOptional.isPresent());
            assertTrue(aliveOptional.get());
        }
    }

    @Test
    void checkAliveWrongKey() throws Exception {
        // should not accept random KEY
        final CipherKey randomCipherKey = Mocks.cipherKey();
        try (CLUClient otherClient = new CLUClient(LOCALHOST, server.getServer().getDevice(), randomCipherKey, LOCALHOST, server.getPort())) {
            final Optional<Boolean> aliveOptional = otherClient.checkAlive();

            assertFalse(aliveOptional.isPresent());
        }
    }

    @Test
    void setAddress() throws Exception {
        try (CLUClient client = new CLUClient(LOCALHOST, server.getServer().getDevice(), projectCipherKey, LOCALHOST, server.getPort())) {
            final Optional<Inet4Address> addressOptional = client.setAddress(LOCALHOST, LOCALHOST);

            assertTrue(addressOptional.isPresent());
            assertEquals(LOCALHOST, addressOptional.get());
        }
    }

    @Test
    void setAddressUsingBroadcast() throws Exception {
        try (CLUClient broadcastClient = new CLUClient(LOCALHOST, server.getServer().getDevice(), projectCipherKey, LOCALHOST, server.getBroadcastPort())) {
            final Optional<Inet4Address> addressOptional = broadcastClient.setAddress(LOCALHOST, LOCALHOST);

            assertTrue(addressOptional.isPresent());
            assertEquals(LOCALHOST, addressOptional.get());
        }
    }

    @Test
    void setAddressUnsupported() throws Exception {
        try (CLUClient client = new CLUClient(LOCALHOST, server.getServer().getDevice(), projectCipherKey, LOCALHOST, server.getPort())) {
            final Optional<Inet4Address> addressOptional = client.setAddress(IPv4AddressUtil.parseIPv4("1.1.1.1"), LOCALHOST);

            assertFalse(addressOptional.isPresent());
        }
    }
}