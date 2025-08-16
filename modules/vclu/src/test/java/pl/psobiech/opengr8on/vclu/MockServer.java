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

import java.io.Closeable;
import java.net.Inet4Address;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.jstach.jstachio.JStachio;
import org.apache.commons.lang3.StringUtils;
import pl.psobiech.opengr8on.client.CLUClient;
import pl.psobiech.opengr8on.client.CLUFiles;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.Mocks;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.client.device.CipherTypeEnum;
import pl.psobiech.opengr8on.tftp.TFTPServer;
import pl.psobiech.opengr8on.tftp.TFTPServer.ServerMode;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.HexUtil;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.ResourceUtil;
import pl.psobiech.opengr8on.util.SocketUtil.UDPSocket;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.vclu.main.MainLuaTemplate;

public class MockServer implements Closeable {
    public static final Inet4Address LOCALHOST = IPv4AddressUtil.parseIPv4("127.0.0.1");

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final Path rootDirectory;

    private final Path aDriveDirectory;

    private final TFTPServer tftpServer;

    private final UDPSocket broadcastSocket;

    private final UDPSocket commandSocket;

    private final UDPSocket responseSocket;

    private final Server server;

    public MockServer(CipherKey projectCipherKey, long serialNumber) throws Exception {
        this(
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
    }

    public MockServer(CipherKey projectCipherKey, CLUDevice cluDevice) throws Exception {
        this.rootDirectory = FileUtil.temporaryDirectory();
        this.aDriveDirectory = rootDirectory.resolve("a");

        FileUtil.mkdir(aDriveDirectory);

        this.broadcastSocket = new UDPSocket(LOCALHOST, 0, false);
        this.commandSocket = new UDPSocket(LOCALHOST, 0, false);
        this.responseSocket = new UDPSocket(LOCALHOST, 0, false);
        this.tftpServer = new TFTPServer(LOCALHOST, 0, ServerMode.GET_AND_REPLACE, rootDirectory);

        this.tftpServer.start();
        this.tftpServer.stop();

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

        this.server = new Server(
                rootDirectory,
                projectCipherKey,
                cluDevice,
                broadcastSocket, commandSocket, responseSocket,
                tftpServer
        );
    }

    public void start() {
        server.start();
    }

    public void execute(CipherKey cipherKey, ServerContext fn) throws Exception {
        start();

        CLUClient client = null;
        try {
            client = new CLUClient(
                    LOCALHOST,
                    getServer().getDevice(),
                    cipherKey,
                    MockServer.LOCALHOST, getPort()
            );

            fn.execute(cipherKey, this, client);
        } finally {
            IOUtil.closeQuietly(client);
        }
    }

    public Path getRootDirectory() {
        return rootDirectory;
    }

    public Path getADriveDirectory() {
        return aDriveDirectory;
    }

    public int getPort() {
        return commandSocket.getLocalPort();
    }

    public int getBroadcastPort() {
        return broadcastSocket.getLocalPort();
    }

    public int getTFTPdPort() {
        return tftpServer.getPort();
    }

    public Server getServer() {
        return server;
    }

    @Override
    public void close() {
        ThreadUtil.closeQuietly(executor);

        IOUtil.closeQuietly(server);

        FileUtil.deleteRecursively(rootDirectory);
    }

    public interface ServerContext {
        void execute(CipherKey projectCipherKey, MockServer mockServer, CLUClient client) throws Exception;
    }
}