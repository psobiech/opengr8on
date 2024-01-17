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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

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
import pl.psobiech.opengr8on.client.commands.LuaScriptCommand;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.ResourceUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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