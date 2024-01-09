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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.jstach.jstachio.JStachio;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import pl.psobiech.opengr8on.client.CLUClient;
import pl.psobiech.opengr8on.client.CLUFiles;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.client.device.CipherTypeEnum;
import pl.psobiech.opengr8on.tftp.TFTPServer;
import pl.psobiech.opengr8on.tftp.TFTPServer.ServerMode;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.HexUtil;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.RandomUtil;
import pl.psobiech.opengr8on.util.ResourceUtil;
import pl.psobiech.opengr8on.util.SocketUtil.UDPSocket;
import pl.psobiech.opengr8on.util.ThreadUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Execution(ExecutionMode.CONCURRENT)
class ServerCommandTest {
    private static final Inet4Address LOCALHOST = IPv4AddressUtil.parseIPv4("127.0.0.1");

    private static ExecutorService executor = Executors.newCachedThreadPool();

    private static Path rootDirectory;

    private static Path aDriveDirectory;

    private static UDPSocket broadcastSocket;

    private static UDPSocket socket;

    private static TFTPServer tftpServer;

    private static CipherKey projectCipherKey;

    private static CLUDevice cluDevice;

    private static Server server;

    private static Future<Void> serverFuture;

    private static CLUClient client;

    private static CLUClient broadcastClient;

    @BeforeAll
    static void setUp() throws Exception {
        executor = Executors.newCachedThreadPool();

        rootDirectory   = FileUtil.temporaryDirectory();
        aDriveDirectory = rootDirectory.resolve("a");

        FileUtil.mkdir(aDriveDirectory);

        broadcastSocket = new UDPSocket(LOCALHOST, 0, false);
        socket          = new UDPSocket(LOCALHOST, 0, false);
        tftpServer      = new TFTPServer(LOCALHOST, 0, ServerMode.GET_AND_REPLACE, rootDirectory);

        projectCipherKey = new CipherKey(RandomUtil.bytes(16), RandomUtil.bytes(16));

        cluDevice = new CLUDevice(
            HexUtil.asLong(RandomUtil.hexString(8)),
            RandomUtil.hexString(12),
            LOCALHOST,
            CipherTypeEnum.PROJECT,
            RandomUtil.bytes(16),
            RandomUtil.hexString(8).getBytes(StandardCharsets.US_ASCII)
        );

        FileUtil.touch(aDriveDirectory.resolve(CLUFiles.USER_LUA.getFileName()));
        FileUtil.linkOrCopy(
            ResourceUtil.classPath(CLUFiles.OM_LUA.getFileName()),
            aDriveDirectory.resolve(CLUFiles.OM_LUA.getFileName())
        );

        final Path mainLuaPath = aDriveDirectory.resolve(CLUFiles.MAIN_LUA.getFileName());
        Files.writeString(
            mainLuaPath,
            JStachio.render(new MainLuaTemplate(StringUtils.lowerCase(StringUtils.leftPad(HexUtil.asString(cluDevice.getSerialNumber()), 8, '0'))))
        );

        server = new Server(
            rootDirectory,
            projectCipherKey,
            cluDevice,
            broadcastSocket, socket,
            tftpServer
        );

        serverFuture = executor.submit(() -> {
            Thread.sleep(100);
            server.listen();

            return null;
        });
        server.awaitInitialized();

        client          = new CLUClient(LOCALHOST, cluDevice, projectCipherKey, LOCALHOST, socket.getLocalPort());
        broadcastClient = new CLUClient(LOCALHOST, cluDevice, projectCipherKey, LOCALHOST, broadcastSocket.getLocalPort());
    }

    @AfterAll
    static void tearDown() throws Exception {
        ThreadUtil.close(executor);

        IOUtil.closeQuietly(client);
        IOUtil.closeQuietly(broadcastClient);

        serverFuture.cancel(true);

        try {
            serverFuture.get();
        } catch (Exception e) {
            //
        }

        IOUtil.closeQuietly(server);

        FileUtil.deleteRecursively(rootDirectory);
    }

    @RepeatedTest(10)
    void checkAlive() throws Exception {
        final Optional<Boolean> aliveOptional = client.checkAlive();

        assertTrue(aliveOptional.isPresent());
        assertTrue(aliveOptional.get());
    }

    @Test
    void checkAliveWrongKey() throws Exception {
        // should not accept random KEY
        final CipherKey randomCipherKey = new CipherKey(RandomUtil.bytes(16), RandomUtil.bytes(16));
        try (CLUClient otherClient = new CLUClient(LOCALHOST, cluDevice, randomCipherKey, LOCALHOST, socket.getLocalPort())) {
            final Optional<Boolean> aliveOptional = otherClient.checkAlive();

            assertFalse(aliveOptional.isPresent());
        }
    }

    @Test
    void discovery() throws Exception {
        // should not accept CLU KEY
        try (CLUClient otherClient = new CLUClient(LOCALHOST, cluDevice, cluDevice.getCipherKey(), LOCALHOST, socket.getLocalPort())) {
            final Optional<Boolean> aliveOptional = otherClient.checkAlive();

            assertFalse(aliveOptional.isPresent());
        }

        final List<CLUDevice> devices = broadcastClient.discover(
                                                           projectCipherKey,
                                                           Map.of(cluDevice.getSerialNumber(), cluDevice.getPrivateKey()),
                                                           Duration.ofMillis(2000L),
                                                           2
                                                       )
                                                       .toList();

        assertEquals(1, devices.size());

        final CLUDevice discoveredDevice = devices.getFirst();
        assertEquals(cluDevice.getName(), discoveredDevice.getName());
        assertEquals(cluDevice.getSerialNumber(), discoveredDevice.getSerialNumber());
        assertEquals(cluDevice.getAddress(), discoveredDevice.getAddress());
        assertEquals(cluDevice.getMacAddress(), discoveredDevice.getMacAddress());
        assertEquals(cluDevice.getCipherType(), discoveredDevice.getCipherType());
        assertEquals(cluDevice.getPrivateKey(), discoveredDevice.getPrivateKey());
        assertEquals(cluDevice.getCipherKey(), discoveredDevice.getCipherKey());

        // should start accepting CLU KEY
        try (CLUClient otherClient = new CLUClient(LOCALHOST, cluDevice, cluDevice.getCipherKey(), LOCALHOST, socket.getLocalPort())) {
            final Optional<Boolean> aliveOptional = otherClient.checkAlive();

            assertTrue(aliveOptional.isPresent());
            assertTrue(aliveOptional.get());
        }

        // should still accept PROJECT KEY
        try (CLUClient otherClient = new CLUClient(LOCALHOST, cluDevice, projectCipherKey, LOCALHOST, socket.getLocalPort())) {
            final Optional<Boolean> aliveOptional = otherClient.checkAlive();

            assertTrue(aliveOptional.isPresent());
            assertTrue(aliveOptional.get());
        }

        final Optional<Boolean> aliveAfterResetOptional = client.reset(Duration.ofMillis(5000L));
        assertTrue(aliveAfterResetOptional.isPresent());
        assertTrue(aliveAfterResetOptional.get());

        // should stop accepting CLU KEY after reset
        try (CLUClient otherClient = new CLUClient(LOCALHOST, cluDevice, cluDevice.getCipherKey(), LOCALHOST, socket.getLocalPort())) {
            final Optional<Boolean> aliveOptional = otherClient.checkAlive();

            assertFalse(aliveOptional.isPresent());
        }
    }

    @Test
    void setAddress() throws Exception {
        final Optional<Inet4Address> addressOptional = client.setAddress(LOCALHOST, LOCALHOST);
        assertTrue(addressOptional.isPresent());
        assertEquals(LOCALHOST, addressOptional.get());
    }

    @Test
    void setAddressUnsupported() throws Exception {
        final Optional<Inet4Address> addressOptional = client.setAddress(IPv4AddressUtil.parseIPv4("1.1.1.1"), LOCALHOST);
        assertFalse(addressOptional.isPresent());
    }

    @Test
    void setAddressUsingBroadcast() throws Exception {
        final Optional<Inet4Address> addressOptional = broadcastClient.setAddress(LOCALHOST, LOCALHOST);
        assertTrue(addressOptional.isPresent());
        assertEquals(LOCALHOST, addressOptional.get());
    }

    @Test
    void startTFTPdServer() throws Exception {
        final Optional<Boolean> responseOptional = client.startTFTPdServer();
        assertTrue(responseOptional.isPresent());
        assertTrue(responseOptional.get());
    }

    @Test
    void stopTFTPdServer() throws Exception {
        final Optional<Boolean> responseOptional = client.stopTFTPdServer();
        assertTrue(responseOptional.isPresent());
        assertTrue(responseOptional.get());
    }
}