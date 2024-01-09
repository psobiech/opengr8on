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
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.jstach.jstachio.JStachio;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import pl.psobiech.opengr8on.client.CLUClient;
import pl.psobiech.opengr8on.client.CLUFiles;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.commands.LuaScriptCommand;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@Execution(ExecutionMode.CONCURRENT)
class ServerTest {
    private static final Inet4Address LOCALHOST = IPv4AddressUtil.parseIPv4("127.0.0.1");

    private ExecutorService executor = Executors.newCachedThreadPool();

    private Path rootDirectory;

    private Path aDriveDirectory;

    private TFTPServer tftpServer;

    private CipherKey projectCipherKey;

    private CLUDevice cluDevice;

    private UDPSocket broadcastSocket;

    private UDPSocket socket;

    private Server server;

    @BeforeEach
    void setUp() throws Exception {
        executor = Executors.newCachedThreadPool();

        rootDirectory = FileUtil.temporaryDirectory();
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
    }

    @AfterEach
    void tearDown() throws Exception {
        ThreadUtil.close(executor);

        IOUtil.closeQuietly(server);

        FileUtil.deleteRecursively(rootDirectory);
    }

    @Test
    @Timeout(30)
    void emergencyMode() throws Exception {
        Files.delete(aDriveDirectory.resolve(CLUFiles.MAIN_LUA.getFileName()));

        runServer(client -> {
            final Optional<String> aliveOptional = client.execute(LuaScriptCommand.CHECK_ALIVE);

            assertTrue(aliveOptional.isPresent());
            assertEquals("emergency", aliveOptional.get());
        });
    }

    @Test
    @Timeout(30)
    void normalMode() throws Exception {
        runServer(client -> {
            final Optional<Boolean> aliveOptional = client.checkAlive();

            assertTrue(aliveOptional.isPresent());
            assertTrue(aliveOptional.get());
        });
    }

    @Test
    void fullMode() throws Exception {
        FileUtil.linkOrCopy(
            ResourceUtil.classPath("full/" + CLUFiles.USER_LUA.getFileName()),
            aDriveDirectory.resolve(CLUFiles.USER_LUA.getFileName())
        );
        FileUtil.linkOrCopy(
            ResourceUtil.classPath("full/" + CLUFiles.OM_LUA.getFileName()),
            aDriveDirectory.resolve(CLUFiles.OM_LUA.getFileName())
        );

        runServer(client -> {
            Thread.sleep(5000L);

            final Optional<Boolean> aliveOptional = client.checkAlive();

            assertTrue(aliveOptional.isPresent());
            assertTrue(aliveOptional.get());
        });
    }

    private void runServer(ServerContext fn) throws Exception {
        final Future<Void> future = executor.submit(() -> {
            Thread.sleep(100);
            server.listen();

            return null;
        });
        server.awaitInitialized();

        CLUClient client = null;
        try {
            client = new CLUClient(LOCALHOST, cluDevice, projectCipherKey, LOCALHOST, socket.getLocalPort());

            fn.execute(client);
        } finally {
            IOUtil.closeQuietly(client);

            future.cancel(true);

            try {
                future.get();
            } catch (Exception e) {
                //
            }
        }
    }

    private interface ServerContext {
        void execute(CLUClient client) throws Exception;
    }
}