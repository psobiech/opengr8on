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

import java.io.IOException;
import java.net.Inet4Address;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import pl.psobiech.opengr8on.client.CLUClient;
import pl.psobiech.opengr8on.client.CLUFiles;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.Mocks;
import pl.psobiech.opengr8on.client.commands.GenerateMeasurementsCommand;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.client.device.CipherTypeEnum;
import pl.psobiech.opengr8on.tftp.TFTPClient;
import pl.psobiech.opengr8on.tftp.TFTPTransferMode;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPException;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.tftp.packets.TFTPErrorType;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.SocketUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
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

    @Test
    @ResourceLock("server")
    void startTFTPdServer() throws Exception {
        final Path rootDirectory = server.getRootDirectory();
        final Path temporaryFile = FileUtil.temporaryFile(rootDirectory);

        assertTFTPdDisabled();

        try (CLUClient client = new CLUClient(LOCALHOST, server.getServer().getDevice(), projectCipherKey, LOCALHOST, server.getPort())) {
            final Optional<Boolean> responseOptional = client.startTFTPdServer();

            assertTrue(responseOptional.isPresent());
            assertTrue(responseOptional.get());
        }

        try (TFTPClient tftpClient = new TFTPClient(SocketUtil.udpRandomPort(LOCALHOST), server.getTFTPdPort())) {
            tftpClient.open();

            tftpClient.download(LOCALHOST, TFTPTransferMode.OCTET, CLUFiles.MAIN_LUA.getLocation(), temporaryFile);
        }

        try (CLUClient client = new CLUClient(LOCALHOST, server.getServer().getDevice(), projectCipherKey, LOCALHOST, server.getPort())) {
            final Optional<Boolean> responseOptional = client.reset(Duration.ofMillis(4000L));

            assertTrue(responseOptional.isPresent());
            assertTrue(responseOptional.get());
        }

        assertTFTPdDisabled();
    }

    @Test
    @ResourceLock("server")
    void generateMeasurements() throws Exception {
        final Path rootDirectory = server.getRootDirectory();
        final Path temporaryFile = FileUtil.temporaryFile(rootDirectory);
        final int sessionId = Mocks.sessionId();

        try (CLUClient client = new CLUClient(LOCALHOST, server.getServer().getDevice(), projectCipherKey, LOCALHOST, server.getPort())) {
            final Optional<GenerateMeasurementsCommand.Response> responseOptional = client.request(
                                                                                              GenerateMeasurementsCommand.request(LOCALHOST, sessionId, "1345"),
                                                                                              Duration.ofMillis(4000L)
                                                                                          )
                                                                                          .flatMap(payload ->
                                                                                              GenerateMeasurementsCommand.responseFromByteArray(payload.buffer())
                                                                                          );

            assertTrue(responseOptional.isPresent());
            assertEquals(sessionId, responseOptional.get().getSessionId());
            assertEquals(GenerateMeasurementsCommand.RESPONSE_OK, responseOptional.get().getReturnValue());
        }

        try (TFTPClient tftpClient = new TFTPClient(SocketUtil.udpRandomPort(LOCALHOST), server.getTFTPdPort())) {
            tftpClient.open();

            tftpClient.download(LOCALHOST, TFTPTransferMode.OCTET, CLUFiles.MAIN_LUA.getLocation(), temporaryFile);
        }

        try (CLUClient client = new CLUClient(LOCALHOST, server.getServer().getDevice(), projectCipherKey, LOCALHOST, server.getPort())) {
            final Optional<Boolean> responseOptional = client.reset(Duration.ofMillis(4000L));

            assertTrue(responseOptional.isPresent());
            assertTrue(responseOptional.get());
        }

        assertTFTPdDisabled();
    }

    private static void assertTFTPdDisabled() throws TFTPPacketException, IOException {
        final int port = server.getTFTPdPort();
        if (port < 1) {
            // TFTPd disabled
            return;
        }

        final Path rootDirectory = server.getRootDirectory();
        final Path temporaryFile = FileUtil.temporaryFile(rootDirectory);

        try (TFTPClient tftpClient = new TFTPClient(SocketUtil.udpRandomPort(LOCALHOST), port)) {
            tftpClient.open();

            try {
                tftpClient.download(LOCALHOST, TFTPTransferMode.OCTET, CLUFiles.MAIN_LUA.getLocation(), temporaryFile);

                fail();
            } catch (TFTPException e) {
                assertEquals(TFTPErrorType.UNDEFINED, e.getError());
            }
        }
    }

    @Test
    @ResourceLock("server")
    void stopTFTPdServer() throws Exception {
        try (CLUClient client = new CLUClient(LOCALHOST, server.getServer().getDevice(), projectCipherKey, LOCALHOST, server.getPort())) {
            final Optional<Boolean> responseOptional = client.stopTFTPdServer();

            assertTrue(responseOptional.isPresent());
            assertTrue(responseOptional.get());
        }
    }
}