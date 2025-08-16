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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.psobiech.opengr8on.tftp.TFTPServer.ServerMode;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPException;
import pl.psobiech.opengr8on.tftp.packets.TFTPErrorType;
import pl.psobiech.opengr8on.tftp.packets.TFTPPacket;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.RandomUtil;
import pl.psobiech.opengr8on.util.SocketUtil;
import pl.psobiech.opengr8on.util.SocketUtil.UDPSocket;
import pl.psobiech.opengr8on.util.ThreadUtil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Execution(ExecutionMode.CONCURRENT)
class TFTPTest {
    private static final Inet4Address LOCALHOST = IPv4AddressUtil.parseIPv4("127.0.0.1");

    private static Path rootDirectory;

    private static UDPSocket socket;

    private static TFTPServer server;

    private static Future<Void> serverFuture;

    private static TFTPClient client;

    @BeforeAll
    static void setUp() throws Exception {
        rootDirectory = FileUtil.temporaryDirectory();
        FileUtil.mkdir(rootDirectory);

        socket = new UDPSocket(LOCALHOST, 0, false);
        server = new TFTPServer(LOCALHOST, ServerMode.GET_AND_PUT, rootDirectory, socket);

        serverFuture = server.start();
        server.awaitInitialized();

        client = new TFTPClient(SocketUtil.udpRandomPort(LOCALHOST), socket.getLocalPort());
    }

    @AfterAll
    static void tearDown() throws Exception {
        ThreadUtil.cancel(serverFuture);

        IOUtil.closeQuietly(client, server);

        FileUtil.deleteRecursively(rootDirectory);
    }

    @ParameterizedTest
    @ValueSource(
            ints = {
                    0,
                    1,
                    1023, 1024, 1025, // multiple blocks
                    0xFFFF * TFTPPacket.MAX_DATA_LENGTH + 1025 // block number rollover
            }
    )
    void uploadBinary(int fileSize) throws Exception {
        final Path temporaryPathFrom = FileUtil.temporaryFile();
        final Path temporaryPathTo = FileUtil.temporaryFile();
        try {
            fillWithRandomBytes(fileSize, temporaryPathFrom);

            final String fileName = "file_" + fileSize + ".bin";

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

            assertEquals(fileSize, Files.size(expectedPath));
            assertEquals(fileSize, Files.size(temporaryPathTo));

            assertFilesSame(temporaryPathFrom, expectedPath);
            assertFilesSame(temporaryPathFrom, temporaryPathTo);
        } finally {
            FileUtil.deleteQuietly(temporaryPathFrom);
            FileUtil.deleteQuietly(temporaryPathTo);
        }
    }

    private static void fillWithRandomBytes(int bufferSize, Path temporaryPathFrom) throws IOException {
        int left = bufferSize;
        try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(temporaryPathFrom))) {
            while (left > 0) {
                final byte[] randomBuffer = RandomUtil.bytes(Math.min(left, 4096));

                outputStream.write(randomBuffer);
                left -= randomBuffer.length;
            }
        }
    }

    private static void assertFilesSame(Path expectedPath, Path actualPath) throws IOException {
        assertEquals(Files.size(expectedPath), Files.size(expectedPath));

        int offset = 0;
        final byte[] expectedBuffer = new byte[1024];
        final byte[] actualBuffer = new byte[1024];
        try (
                InputStream expectedInputStream = new BufferedInputStream(Files.newInputStream(expectedPath));
                InputStream actualInputStream = new BufferedInputStream(Files.newInputStream(actualPath));
        ) {
            int expectedRead;
            int actualRead;
            do {
                expectedRead = expectedInputStream.readNBytes(expectedBuffer, 0, expectedBuffer.length);
                actualRead = actualInputStream.readNBytes(actualBuffer, 0, actualBuffer.length);

                assertArrayEquals(
                        Arrays.copyOf(expectedBuffer, expectedRead), Arrays.copyOf(actualBuffer, actualRead),
                        "Files differ after offset: " + offset
                );

                offset += expectedRead;
            } while (expectedRead > 0 && actualRead > 0);
        }
    }

    @Test
    void uploadDownloadTextAsciiLF() throws Exception {
        final String expectedString = "Some test string" + System.lineSeparator() + "and a second line" + System.lineSeparator() + " ;-)";
        final String inputString = "Some test string" + FileUtil.LF + "and a second line" + FileUtil.LF + " ;-)";

        final Path temporaryPathFrom = FileUtil.temporaryFile();
        final Path temporaryPathTo = FileUtil.temporaryFile();
        try {
            Files.writeString(temporaryPathFrom, inputString);

            final String fileName = "uploadDownloadTextAsciiLF.lua";

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
    void uploadDownloadTextAsciiCRLF() throws Exception {
        final String expectedString = "Some test string" + System.lineSeparator() + "and a second line" + System.lineSeparator() + " ;-)";
        final String inputString = "Some test string" + FileUtil.CR + FileUtil.LF + "and a second line" + FileUtil.CR + FileUtil.LF + " ;-)";

        final Path temporaryPathFrom = FileUtil.temporaryFile();
        final Path temporaryPathTo = FileUtil.temporaryFile();
        try {
            Files.writeString(temporaryPathFrom, inputString);

            final String fileName = "uploadTextAsciiCRLF.lua";

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
    void downloadTextAsciiLF() throws Exception {
        final String expectedString = "Some test string" + System.lineSeparator() + "and a second line" + System.lineSeparator() + " ;-)";
        final String inputString = "Some test string" + FileUtil.LF + "and a second line" + FileUtil.LF + " ;-)";

        final Path temporaryPathFrom = FileUtil.temporaryFile();
        final Path temporaryPathTo = FileUtil.temporaryFile();
        try {
            final String fileName = "downloadTextAsciiLF.lua";

            final Path expectedPath = rootDirectory.resolve(fileName);
            assertFalse(Files.exists(expectedPath));

            Files.writeString(expectedPath, inputString);

            client.download(
                    LOCALHOST, TFTPTransferMode.NETASCII,
                    fileName,
                    temporaryPathTo
            );

            assertEquals(expectedString, Files.readString(temporaryPathTo));
        } finally {
            FileUtil.deleteQuietly(temporaryPathFrom);
            FileUtil.deleteQuietly(temporaryPathTo);
        }
    }

    @Test
    void downloadTextAsciiCRLF() throws Exception {
        final String expectedString = "Some test string" + System.lineSeparator() + "and a second line" + System.lineSeparator() + " ;-)";
        final String inputString = "Some test string" + FileUtil.CR + FileUtil.LF + "and a second line" + FileUtil.CR + FileUtil.LF + " ;-)";

        final Path temporaryPathFrom = FileUtil.temporaryFile();
        final Path temporaryPathTo = FileUtil.temporaryFile();
        try {
            final String fileName = "downloadTextAsciiCRLF.lua";

            final Path expectedPath = rootDirectory.resolve(fileName);
            assertFalse(Files.exists(expectedPath));

            Files.writeString(expectedPath, inputString);

            client.download(
                    LOCALHOST, TFTPTransferMode.NETASCII,
                    fileName,
                    temporaryPathTo
            );

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
            } catch (TFTPException e) {
                assertEquals(TFTPErrorType.FILE_NOT_FOUND, e.getError());
            }

            assertFalse(Files.exists(expectedPath));
        } finally {
            FileUtil.deleteQuietly(temporaryPathTo);
        }
    }

    @Test
    void downloadDirectory() throws Exception {
        final Path temporaryPathTo = FileUtil.temporaryFile();
        try {
            final String fileName = "imadirectory";

            final Path expectedPath = rootDirectory.resolve(fileName);
            FileUtil.mkdir(expectedPath);

            try {
                client.download(
                        LOCALHOST, TFTPTransferMode.NETASCII,
                        fileName,
                        temporaryPathTo
                );
            } catch (TFTPException e) {
                assertEquals(TFTPErrorType.UNDEFINED, e.getError());
            }
        } finally {
            FileUtil.deleteQuietly(temporaryPathTo);
        }
    }

    @Test
    void uploadToDirectory() throws Exception {
        final Path temporaryPathFrom = FileUtil.temporaryFile();
        try {
            final String fileName = "imadirectory";

            final Path expectedPath = rootDirectory.resolve(fileName);
            FileUtil.mkdir(expectedPath);

            try {
                client.upload(
                        LOCALHOST, TFTPTransferMode.NETASCII,
                        temporaryPathFrom,
                        fileName
                );
            } catch (TFTPException e) {
                assertEquals(TFTPErrorType.FILE_EXISTS, e.getError());
            }
        } finally {
            FileUtil.deleteQuietly(temporaryPathFrom);
        }
    }
}