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

public class InternalMessage extends Message {
    private final SensorCommandType commandEnum;

    private final SensorInternalMessageType typeEnum;

    InternalMessage(int nodeId, int childSensorId, SensorCommandType commandEnum, int ack, SensorInternalMessageType typeEnum, String payload) {
        super(nodeId, childSensorId, commandEnum.type(), ack, typeEnum.type(), payload);

        this.commandEnum = commandEnum;
        this.typeEnum    = typeEnum;
    }

    @Override
    public SensorCommandType getCommandEnum() {
        return commandEnum;
    }

    @Override
    public SensorInternalMessageType getTypeEnum() {
        return typeEnum;
    }

    @Override
    public String toString() {
        return "InternalMessage{" +
               "commandEnum=" + commandEnum +
               ", typeEnum=" + typeEnum +
               "} " + super.toString();
    }

    public enum SensorInternalMessageType implements TypeEnum {
        I_BATTERY_LEVEL(0), // Battery level
        I_TIME(1), // Time (request/response)
        I_VERSION(2), // Version
        I_ID_REQUEST(3), // ID request
        I_ID_RESPONSE(4), // ID response
        I_INCLUSION_MODE(5), // Inclusion mode
        I_CONFIG(6), // Config (request/response)
        I_FIND_PARENT_REQUEST(7), // Find parent
        I_FIND_PARENT_RESPONSE(8), // Find parent response
        I_LOG_MESSAGE(9), // Log message
        I_CHILDREN(10), // Children
        I_SKETCH_NAME(11), // Sketch name
        I_SKETCH_VERSION(12), // Sketch version
        I_REBOOT(13), // Reboot request
        I_GATEWAY_READY(14), // Gateway ready
        I_SIGNING_PRESENTATION(15), // Provides signing related preferences (first byte is preference version)
        I_NONCE_REQUEST(16), // Request for a nonce
        I_NONCE_RESPONSE(17), // Payload is nonce data
        I_HEARTBEAT_REQUEST(18), // Heartbeat request
        I_PRESENTATION(19), // Presentation message
        I_DISCOVER_REQUEST(20), // Discover request
        I_DISCOVER_RESPONSE(21), // Discover response
        I_HEARTBEAT_RESPONSE(22), // Heartbeat response
        I_LOCKED(23), // Node is locked (reason in string-payload)
        I_PING(24), // Ping sent to node, payload incremental hop counter
        I_PONG(25), // In return to ping, sent back to sender, payload incremental hop counter
        I_REGISTRATION_REQUEST(26), // Register request to GW
        I_REGISTRATION_RESPONSE(27), // Register response from GW
        I_DEBUG(28), // Debug message
        I_SIGNAL_REPORT_REQUEST(29), // Device signal strength request
        I_SIGNAL_REPORT_REVERSE(30), // Internal
        I_SIGNAL_REPORT_RESPONSE(31), // Device signal strength response (RSSI)
        I_PRE_SLEEP_NOTIFICATION(32), // message.Message sent before node is going to sleep
        I_POST_SLEEP_NOTIFICATION(33), // message.Message sent after node woke up (if enabled)
        //
        ;

        private final int type;

        SensorInternalMessageType(int type) {
            this.type = type;
        }

        public static Optional<SensorInternalMessageType> byType(int type) {
            for (SensorInternalMessageType sensorType : values()) {
                if (type == sensorType.type()) {
                    return Optional.of(sensorType);
                }
            }

            return Optional.empty();
        }

        public int type() {
            return type;
        }
    }
}
