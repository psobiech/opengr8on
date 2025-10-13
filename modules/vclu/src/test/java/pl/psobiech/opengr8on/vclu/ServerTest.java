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

import io.moquette.broker.ClientDescriptor;
import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;
import io.moquette.interception.AbstractInterceptHandler;
import io.moquette.interception.messages.InterceptAcknowledgedMessage;
import io.moquette.interception.messages.InterceptPublishMessage;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.junit.jupiter.api.Disabled;
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
import pl.psobiech.opengr8on.util.ResourceUtil;
import pl.psobiech.opengr8on.util.SocketUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static pl.psobiech.opengr8on.vclu.MockServer.LOCALHOST;

class ServerTest extends BaseServerTest {
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
    @Timeout(30)
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

    @Test
    @Disabled
    @Timeout(30)
    void fullMode() throws Exception {
        final Server mqttServer = new Server();

        final Properties properties = new Properties();
        properties.setProperty("port", Integer.toString(1883));
        properties.setProperty("host", MockServer.LOCALHOST.getHostAddress());
        properties.setProperty("password_file", "");
        properties.setProperty("allow_anonymous", Boolean.TRUE.toString());
        properties.setProperty("authenticator_class", "");
        properties.setProperty("authorizator_class", "");

        final List<String> testTopicMessages = Collections.synchronizedList(new LinkedList<>());

        mqttServer.startServer(new MemoryConfig(properties));
        try {
            mqttServer.addInterceptHandler(new AbstractInterceptHandler() {
                @Override
                public String getID() {
                    return "test";
                }

                @Override
                public void onPublish(InterceptPublishMessage msg) {
                    if (msg.getTopicName().equals("test")) {
                        testTopicMessages.add(msg.getPayload().toString(StandardCharsets.UTF_8));
                    }
                }

                @Override
                public void onMessageAcknowledged(InterceptAcknowledgedMessage msg) {
                    //
                }
            });

            execute(
                    server -> {
                        FileUtil.linkOrCopy(
                                ResourceUtil.classPath("full/" + CLUFiles.USER_LUA.getFileName()),
                                server.getADriveDirectory().resolve(CLUFiles.USER_LUA.getFileName())
                        );
                        FileUtil.linkOrCopy(
                                ResourceUtil.classPath("full/" + CLUFiles.OM_LUA.getFileName()),
                                server.getADriveDirectory().resolve(CLUFiles.OM_LUA.getFileName())
                        );
                    },
                    (projectCipherKey, server, client) -> {
                        final Optional<Boolean> aliveOptional = client.checkAlive();

                        assertTrue(aliveOptional.isPresent());
                        assertTrue(aliveOptional.get());

                        final Collection<ClientDescriptor> clientDescriptors = mqttServer.listConnectedClients();
                        while (clientDescriptors.isEmpty()) {
                            Thread.sleep(100L);
                        }

                        assertEquals("CLU0", clientDescriptors.iterator().next().getClientID());

                        mqttServer.internalPublish(
                                MqttMessageBuilders.publish()
                                                   .topicName("zigbee2mqtt/testTopic")
                                                   .retained(false)
                                                   .messageId(1)
                                                   .qos(MqttQoS.AT_LEAST_ONCE)
                                                   .payload(Unpooled.copiedBuffer("mqttTest".getBytes(StandardCharsets.UTF_8)))
                                                   .build(),
                                "BROKER"
                        );

                        mqttServer.internalPublish(
                                MqttMessageBuilders.publish()
                                                   .topicName("zigbee2mqtt/otherTopic")
                                                   .retained(false)
                                                   .messageId(2)
                                                   .qos(MqttQoS.AT_LEAST_ONCE)
                                                   .payload(Unpooled.copiedBuffer("mqttTestOther".getBytes(StandardCharsets.UTF_8)))
                                                   .build(),
                                "BROKER"
                        );

                        while (testTopicMessages.size() < 2) {
                            Thread.sleep(100L);
                        }

                        assertEquals(2, testTopicMessages.size());
                        assertEquals("mqttTest", testTopicMessages.get(0));
                        assertEquals("mqttTestOther", testTopicMessages.get(1));
                    }
            );
        } finally {
            mqttServer.stopServer();
        }
    }
}