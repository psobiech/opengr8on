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

package pl.psobiech.opengr8on.mysensors;

import java.util.Hashtable;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.mysensors.message.DataMessage.SensorDataType;
import pl.psobiech.opengr8on.mysensors.message.Message;
import pl.psobiech.opengr8on.mysensors.message.Message.SensorCommandType;
import pl.psobiech.opengr8on.mysensors.message.MessageFactory;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.SocketUtil;
import pl.psobiech.opengr8on.util.ThreadUtil;

import static pl.psobiech.opengr8on.mysensors.message.InternalMessage.SensorInternalMessageType.I_HEARTBEAT_REQUEST;

class MySensorsClientTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MySensorsClient.class);

    @Test
    @Disabled
    void main() throws Exception {
        final ScheduledExecutorService executorService = ThreadUtil.virtualScheduler("MySensors");

        final MySensorsClient client = new MySensorsClient(
            executorService,
            SocketUtil.tcpClient(IPv4AddressUtil.parseIPv4("192.168.0.240"), 5003)
        );

        final Hashtable<String, String> names = new Hashtable<>();

        executorService.submit(() -> {
            while (!Thread.interrupted()) {
                Thread.sleep(10_000);

                client.send(
                    MessageFactory.internal(
                        0, 255,
                        I_HEARTBEAT_REQUEST
                    )
                );
            }

            return null;
        });

        final AtomicInteger nodeIdSequence = new AtomicInteger(0);

        client.open(
            (mySensorsClient, internalMessage) -> nodeIdSequence.incrementAndGet(),
            (controller, message) -> {
                names.put(message.getNodeId() + ":" + message.getChildSensorId(), message.getPayload());

                final Optional<Message> responseMessage = switch (message.getTypeEnum()) {
                    case S_BINARY -> {
                        executorService.schedule(() ->
                                client.send(
                                    MessageFactory.set(
                                        message.getNodeId(), message.getChildSensorId(),
                                        0,
                                        SensorDataType.V_STATUS,
                                        "0"
                                    )
                                ),
                            2000, TimeUnit.MILLISECONDS
                        );

                        yield Optional.of(
                            MessageFactory.set(
                                message.getNodeId(), message.getChildSensorId(),
                                0,
                                SensorDataType.V_STATUS,
                                "1"
                            )
                        );
                    }
                    default -> Optional.empty();
                };

                responseMessage.ifPresent(controller::send);
            },
            (controller, message) -> {
                if (message.getCommandEnum() == SensorCommandType.C_SET) {
                    LOGGER.info("SET: {}:{} ({}) == {}",
                        message.getNodeId(), message.getChildSensorId(),
                        names.get(message.getNodeId() + ":" + message.getChildSensorId()),
                        switch (message.getTypeEnum()) {
                            case V_TEMP -> message.getPayloadAsFloat();
                            case V_STATUS -> message.getPayloadAsBoolean();
                            default -> message.getPayload();
                        }
                    );
                }
            }
        );

        new CountDownLatch(1).await();
    }
}