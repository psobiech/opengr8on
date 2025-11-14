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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import pl.psobiech.opengr8on.client.CLUFiles;
import pl.psobiech.opengr8on.client.Mocks;
import pl.psobiech.opengr8on.client.commands.GenerateMeasurementsCommand;
import pl.psobiech.opengr8on.client.commands.LuaScriptCommand;
import pl.psobiech.opengr8on.tftp.TFTPClient;
import pl.psobiech.opengr8on.tftp.TFTPTransferMode;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPException;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.tftp.packets.TFTPErrorType;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.SocketUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static pl.psobiech.opengr8on.vclu.MockServer.LOCALHOST;

class ServerTest extends BaseServerTest {
    @Test
    @Timeout(30)
    void normalMode() throws Exception {
        execute((projectCipherKey, server, client) -> {
            final Optional<Boolean> aliveOptional = client.checkAlive();

            assertTrue(aliveOptional.isPresent());
            assertTrue(aliveOptional.get());
        });
    }

    @Test
    @Timeout(60)
    void emergencyMode() throws Exception {
        execute(
                server -> {
                    try {
                        Files.delete(server.getADriveDirectory().resolve(CLUFiles.MAIN_LUA.getFileName()));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                (projectCipherKey, server, client) -> {
                    final Optional<String> aliveOptional = client.execute(LuaScriptCommand.CHECK_ALIVE);

                    assertTrue(aliveOptional.isPresent());
                    assertEquals("emergency", aliveOptional.get());
                }
        );
    }

    @Test
    void startTFTPdServer() throws Exception {
        execute(
                (projectCipherKey, server, client) -> {
                    assertTFTPdDisabled(server);

                    final Path rootDirectory = server.getRootDirectory();
                    final Path temporaryFile = FileUtil.temporaryFile(rootDirectory);

                    final Optional<Boolean> response1Optional = client.startTFTPdServer();
                    assertTrue(response1Optional.isPresent());
                    assertTrue(response1Optional.get());

                    try (TFTPClient tftpClient = new TFTPClient(SocketUtil.udpRandomPort(LOCALHOST), server.getTFTPdPort())) {
                        tftpClient.download(LOCALHOST, TFTPTransferMode.OCTET, CLUFiles.MAIN_LUA.getLocation(), temporaryFile);
                    }

                    final Optional<Boolean> response2Optional = client.reset(Duration.ofMillis(4000L));
                    assertTrue(response2Optional.isPresent());
                    assertTrue(response2Optional.get());

                    assertTFTPdDisabled(server);
                }
        );
    }

    @Test
    void generateMeasurements() throws Exception {
        execute(
                (projectCipherKey, server, client) -> {
                    assertTFTPdDisabled(server);

                    final Path rootDirectory = server.getRootDirectory();
                    final Path temporaryFile = FileUtil.temporaryFile(rootDirectory);
                    final int sessionId = Mocks.sessionId();

                    final Optional<GenerateMeasurementsCommand.Response> response1Optional = client.request(
                                                                                                           GenerateMeasurementsCommand.request(LOCALHOST, sessionId, "1345"),
                                                                                                           Duration.ofMillis(4000L)
                                                                                                   )
                                                                                                   .flatMap(payload ->
                                                                                                                    GenerateMeasurementsCommand.responseFromByteArray(payload.buffer())
                                                                                                   );

                    assertTrue(response1Optional.isPresent());
                    assertEquals(sessionId, response1Optional.get().getSessionId());
                    assertEquals(GenerateMeasurementsCommand.RESPONSE_OK, response1Optional.get().getReturnValue());

                    try (TFTPClient tftpClient = new TFTPClient(SocketUtil.udpRandomPort(LOCALHOST), server.getTFTPdPort())) {
                        tftpClient.download(LOCALHOST, TFTPTransferMode.OCTET, CLUFiles.MAIN_LUA.getLocation(), temporaryFile);
                    }

                    final Optional<Boolean> response2Optional = client.reset(Duration.ofMillis(4000L));
                    assertTrue(response2Optional.isPresent());
                    assertTrue(response2Optional.get());

                    assertTFTPdDisabled(server);
                }
        );
    }

    @Test
    void stopTFTPdServer() throws Exception {
        execute(
                (projectCipherKey, server, client) -> {
                    final Optional<Boolean> responseOptional = client.stopTFTPdServer();

                    assertTrue(responseOptional.isPresent());
                    assertTrue(responseOptional.get());

                    assertTFTPdDisabled(server);
                });
    }

    private static void assertTFTPdDisabled(MockServer server) throws TFTPPacketException, IOException {
        final int port = server.getTFTPdPort();
        if (port < 1) {
            // TFTPd disabled
            return;
        }

        final Path rootDirectory = server.getRootDirectory();
        final Path temporaryFile = FileUtil.temporaryFile(rootDirectory);
        try (TFTPClient tftpClient = new TFTPClient(SocketUtil.udpRandomPort(LOCALHOST), port)) {
            try {
                tftpClient.download(LOCALHOST, TFTPTransferMode.OCTET, CLUFiles.MAIN_LUA.getLocation(), temporaryFile);

                fail();
            } catch (TFTPException e) {
                assertEquals(TFTPErrorType.UNDEFINED, e.getError());
            }
        }
    }
}