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

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import pl.psobiech.opengr8on.mysensors.message.InternalMessage.SensorInternalMessageType;
import pl.psobiech.opengr8on.mysensors.message.Message;
import pl.psobiech.opengr8on.mysensors.message.Message.SensorCommandType;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.SocketUtil;
import pl.psobiech.opengr8on.util.ThreadUtil;

class MySensorsClientTest {
    public static final int ANY_NODE = 255;

    @Test
    @Disabled
    void main() throws Exception {
        final ExecutorService executorService = ThreadUtil.virtualExecutor("MySensors");

        final MySensorsClient client = new MySensorsClient(
            executorService,
            SocketUtil.tcpClient(IPv4AddressUtil.parseIPv4("192.168.0.240"), 5003)
        );
        client.open();

        client.send(
            Message.internal(
                ANY_NODE, ANY_NODE,
                SensorInternalMessageType.I_DISCOVER_REQUEST
            )
        );

        executorService.submit(() -> {
            while (!Thread.interrupted()) {
                Thread.sleep(8000);

                client.send(
                    Message.internal(
                        1, ANY_NODE,
                        SensorInternalMessageType.I_HEARTBEAT_REQUEST
                    )
                );
            }

            return null;
        });

        final AtomicInteger nextNode = new AtomicInteger(0);
        do {
            final Optional<Message> rawMessageOptional = client.poll();
            if (rawMessageOptional.isEmpty()) {
                continue;
            }

            final Message message = rawMessageOptional.get();
            if (message.requiresEcho()) {
                client.echo(message);
            }

            if (message.getCommandEnum() == SensorCommandType.C_INTERNAL) {
                final SensorInternalMessageType typeEnum = (SensorInternalMessageType) message.getTypeEnum();
                switch (typeEnum) {
                    case SensorInternalMessageType.I_ID_REQUEST -> client.send(
                        Message.idResponse(
                            message.getNodeId(), message.getChildSensorId(),
                            nextNode.incrementAndGet()
                        )
                    );
                    case SensorInternalMessageType.I_GATEWAY_READY -> client.send(
                        Message.internal(
                            message.getNodeId(), ANY_NODE,
                            SensorInternalMessageType.I_VERSION
                        )
                    );
                    case SensorInternalMessageType.I_CONFIG -> client.send(
                        Message.internal(
                            message.getNodeId(), message.getChildSensorId(),
                            SensorInternalMessageType.I_CONFIG,
                            "M" // METRIC
                        )
                    );
                    case SensorInternalMessageType.I_DISCOVER_RESPONSE -> client.send(
                        Message.internal(
                            message.getNodeId(), ANY_NODE,
                            SensorInternalMessageType.I_PRESENTATION
                        )
                    );
                }
            }
        } while (!Thread.interrupted());
    }
}