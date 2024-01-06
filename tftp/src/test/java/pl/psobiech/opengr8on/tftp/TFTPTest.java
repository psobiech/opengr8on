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

package pl.psobiech.opengr8on.tftp;

import java.net.Inet4Address;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.psobiech.opengr8on.tftp.TFTPServer.ServerMode;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.tftp.packets.TFTPErrorType;
import pl.psobiech.opengr8on.tftp.packets.TFTPPacket;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.RandomUtil;
import pl.psobiech.opengr8on.util.SocketUtil;
import pl.psobiech.opengr8on.util.SocketUtil.UDPSocket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Execution(ExecutionMode.CONCURRENT)
class TFTPTest {
    private static final Inet4Address LOCALHOST = IPv4AddressUtil.parseIPv4("127.0.0.1");

    private static ExecutorService executor = Executors.newCachedThreadPool();

    private static Path rootDirectory;

    private static UDPSocket socket;

    private static TFTPServer server;

    private static Future<Void> serverFuture;

    private static TFTPClient client;

    @BeforeAll
    static void setUp() throws Exception {
        executor = Executors.newCachedThreadPool();

        rootDirectory = FileUtil.temporaryDirectory();
        FileUtil.mkdir(rootDirectory);

        socket = new UDPSocket(LOCALHOST, 0, false);
        server = new TFTPServer(LOCALHOST, ServerMode.GET_AND_PUT, rootDirectory, socket);

        serverFuture = server.start();
        server.awaitInitialized();

        client = new TFTPClient(SocketUtil.udpRandomPort(LOCALHOST), socket.getLocalPort());
        client.open();
    }

    @AfterAll
    static void tearDown() throws Exception {
        FileUtil.closeQuietly(client);

        serverFuture.cancel(true);
        try {
            serverFuture.get();
        } catch (Exception e) {
            //
        }

        FileUtil.closeQuietly(server);
        executor.shutdownNow();

        FileUtil.deleteRecursively(rootDirectory);
    }

    @ParameterizedTest
    @ValueSource(
        ints = {
            0,
            1,
            1023, 1024, 1025, // multiple blocks
            (0xFFFF + 513) * TFTPPacket.SEGMENT_SIZE // block number rollover
        }
    )
    void uploadBinary(int bufferSize) throws Exception {
        final byte[] expectedBuffer = RandomUtil.bytes(bufferSize);

        final Path temporaryPathFrom = FileUtil.temporaryFile();
        final Path temporaryPathTo = FileUtil.temporaryFile();
        try {
            Files.write(temporaryPathFrom, expectedBuffer);

            final String fileName = "file_" + bufferSize + ".bin";

            final Path expectedPath = rootDirectory.resolve(fileName);
            assertFalse(Files.exists(expectedPath));

            client.upload(
                LOCALHOST, TFTPTransferMode.OCTET,
                temporaryPathFrom,
                fileName
            );

            client.download(
                LOCALHOST, TFTPTransferMode.OCTET,
                fileName,
                temporaryPathTo
            );

            assertTrue(Files.exists(expectedPath));

            assertEquals(expectedBuffer.length, Files.size(expectedPath));
            assertArrayEquals(expectedBuffer, Files.readAllBytes(expectedPath));

            assertEquals(expectedBuffer.length, Files.size(temporaryPathTo));
            assertArrayEquals(expectedBuffer, Files.readAllBytes(temporaryPathTo));
        } finally {
            FileUtil.deleteQuietly(temporaryPathFrom);
            FileUtil.deleteQuietly(temporaryPathTo);
        }
    }

    @Test
    void uploadTextAscii() throws Exception {
        final String expectedString = "Some test string\nand a second line\n";

        final Path temporaryPathFrom = FileUtil.temporaryFile();
        final Path temporaryPathTo = FileUtil.temporaryFile();
        try {
            Files.writeString(temporaryPathFrom, expectedString);

            final String fileName = "main.lua";

            final Path expectedPath = rootDirectory.resolve(fileName);
            assertFalse(Files.exists(expectedPath));

            client.upload(
                LOCALHOST, TFTPTransferMode.NETASCII,
                temporaryPathFrom,
                fileName
            );

            client.download(
                LOCALHOST, TFTPTransferMode.NETASCII,
                fileName,
                temporaryPathTo
            );

            assertTrue(Files.exists(expectedPath));
            assertEquals(expectedString, Files.readString(expectedPath));
            assertEquals(expectedString, Files.readString(temporaryPathTo));
        } finally {
            FileUtil.deleteQuietly(temporaryPathFrom);
            FileUtil.deleteQuietly(temporaryPathTo);
        }
    }

    @Test
    void downloadNotFound() throws Exception {
        final Path temporaryPathTo = FileUtil.temporaryFile();
        try {
            final String fileName = "notexisting.lua";

            final Path expectedPath = rootDirectory.resolve(fileName);
            assertFalse(Files.exists(expectedPath));

            try {
                client.download(
                    LOCALHOST, TFTPTransferMode.NETASCII,
                    fileName,
                    temporaryPathTo
                );
            } catch (TFTPPacketException e) {
                assertEquals(TFTPErrorType.FILE_NOT_FOUND, e.getError());
            }

            assertFalse(Files.exists(expectedPath));
        } finally {
            FileUtil.deleteQuietly(temporaryPathTo);
        }
    }
}