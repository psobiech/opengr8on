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

package pl.psobiech.opengr8on.mysensors.message;

import java.util.Optional;

import pl.psobiech.opengr8on.mysensors.message.DataMessage.SensorDataType;
import pl.psobiech.opengr8on.mysensors.message.InternalMessage.SensorInternalMessageType;
import pl.psobiech.opengr8on.mysensors.message.Message.SensorCommandType;
import pl.psobiech.opengr8on.mysensors.message.PresentationMessage.SensorType;

public class MessageFactory {
    private static final int MESSAGE_PARTS = 6;

    private static final int NODE_ID_PART = 0;

    private static final int SENSOR_CHILD_ID_PART = 1;

    private static final int COMMAND_PART = 2;

    private static final int ECHO_PART = 3;

    private static final int TYPE_PART = 4;

    private static final int PAYLOAD_PART = 5;

    private static final String PART_SEPARATOR = ";";

    private MessageFactory() {
        // NOP
    }

    public static Optional<Message> parseMessage(String messageAsString) {
        final String[] parts = messageAsString.split(PART_SEPARATOR, MESSAGE_PARTS);
        if (parts.length != MESSAGE_PARTS) {
            return Optional.empty();
        }

        final int nodeId = Integer.parseInt(parts[NODE_ID_PART]);
        final int sensorChildId = Integer.parseInt(parts[SENSOR_CHILD_ID_PART]);

        final int command = Integer.parseInt(parts[COMMAND_PART]);

        final int echo = Integer.parseInt(parts[ECHO_PART]);

        final int type = Integer.parseInt(parts[TYPE_PART]);
        final String payload = parts[PAYLOAD_PART];

        return SensorCommandType.byType(command)
                                .flatMap(commandType ->
                                    parseMessage(nodeId, sensorChildId, echo, commandType, type, payload)
                                );
    }

    public static Optional<? extends Message> parseMessage(int nodeId, int sensorChildId, int echo, SensorCommandType commandType, int type, String payload) {
        return switch (commandType) {
            case C_PRESENTATION -> parsePresentationMessage(nodeId, sensorChildId, echo, type, payload);
            case C_SET -> parseSetMessage(nodeId, sensorChildId, echo, type, payload);
            case C_REQ -> parseRequestMessage(nodeId, sensorChildId, echo, type, payload);
            case C_INTERNAL -> parseInternalMessage(nodeId, sensorChildId, echo, type, payload);
            default -> Optional.empty();
        };
    }

    private static Optional<PresentationMessage> parsePresentationMessage(int nodeId, int sensorChildId, int echo, int type, String payload) {
        return SensorType.byType(type)
                         .map(sensorType ->
                             MessageFactory.presentation(
                                 nodeId, sensorChildId,
                                 echo,
                                 sensorType, payload
                             )
                         );
    }

    private static Optional<DataMessage> parseSetMessage(int nodeId, int sensorChildId, int echo, int type, String payload) {
        return SensorDataType.byType(type)
                             .map(sensorDataType ->
                                 MessageFactory.set(
                                     nodeId, sensorChildId,
                                     echo,
                                     sensorDataType, payload
                                 )
                             );
    }

    private static Optional<DataMessage> parseRequestMessage(int nodeId, int sensorChildId, int echo, int type, String payload) {
        return SensorDataType.byType(type)
                             .map(sensorDataType ->
                                 MessageFactory.request(
                                     nodeId, sensorChildId,
                                     echo,
                                     sensorDataType, payload
                                 )
                             );
    }

    private static Optional<InternalMessage> parseInternalMessage(int nodeId, int sensorChildId, int echo, int type, String payload) {
        return SensorInternalMessageType.byType(type)
                                        .map(sensorInternalMessageType ->
                                            MessageFactory.internal(
                                                nodeId, sensorChildId,
                                                echo,
                                                sensorInternalMessageType, payload
                                            )
                                        );
    }

    public static DataMessage request(int nodeId, int childSensorId, int echo, SensorDataType type, String payload) {
        return new DataMessage(nodeId, childSensorId, SensorCommandType.C_REQ, echo, type, payload);
    }

    public static DataMessage set(int nodeId, int childSensorId, int echo, SensorDataType type, String payload) {
        return new DataMessage(nodeId, childSensorId, SensorCommandType.C_SET, echo, type, payload);
    }

    public static PresentationMessage presentation(int nodeId, int childSensorId, int echo, SensorType type, String payload) {
        return new PresentationMessage(nodeId, childSensorId, SensorCommandType.C_PRESENTATION, echo, type, payload);
    }

    public static InternalMessage idResponse(int nodeId, int childSensorId, int newNodeId) {
        return internal(nodeId, childSensorId, SensorInternalMessageType.I_ID_RESPONSE, newNodeId);
    }

    public static InternalMessage internal(int nodeId, int childSensorId, SensorInternalMessageType type) {
        return internal(nodeId, childSensorId, Message.VALUE_FALSE, type, null);
    }

    public static InternalMessage internal(int nodeId, int childSensorId, SensorInternalMessageType type, int payload) {
        return new InternalMessage(nodeId, childSensorId, SensorCommandType.C_INTERNAL, 0, type, String.valueOf(payload));
    }

    public static InternalMessage internal(int nodeId, int childSensorId, SensorInternalMessageType type, String payload) {
        return new InternalMessage(nodeId, childSensorId, SensorCommandType.C_INTERNAL, 0, type, payload);
    }

    public static InternalMessage internal(int nodeId, int childSensorId, int echo, SensorInternalMessageType type, String payload) {
        return new InternalMessage(nodeId, childSensorId, SensorCommandType.C_INTERNAL, echo, type, payload);
    }
}
