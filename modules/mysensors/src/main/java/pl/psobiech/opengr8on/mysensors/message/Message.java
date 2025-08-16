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

import org.apache.commons.lang3.StringUtils;

public class Message {
    public static final int VALUE_FALSE = 0;

    public static final int VALUE_TRUE = 1;

    public static final String VALUE_TRUE_AS_STRING = String.valueOf(VALUE_TRUE);

    protected final int nodeId;

    protected final int childSensorId;

    protected final int command;

    protected final int echo;

    protected final int type;

    protected final String payload;

    public Message(int nodeId, int childSensorId, int command, int echo, int type, String payload) {
        this.nodeId = nodeId;
        this.childSensorId = childSensorId;
        this.command = command;
        this.echo = echo;
        this.type = type;
        this.payload = payload;
    }

    public String serialize(boolean ignoreEcho) {
        return getNodeId()
                + ";"
                + getChildSensorId()
                + ";"
                + getCommand()
                + ";"
                + (ignoreEcho ? VALUE_FALSE : getEcho())
                + ";"
                + getType()
                + ";"
                + StringUtils.stripToEmpty(getPayload());
    }

    public int getNodeId() {
        return nodeId;
    }

    public int getChildSensorId() {
        return childSensorId;
    }

    public SensorCommandType getCommandEnum() {
        return SensorCommandType.byType(getCommand())
                .get();
    }

    public int getCommand() {
        return command;
    }

    public boolean requiresEcho() {
        return getEcho() == VALUE_TRUE;
    }

    public int getEcho() {
        return echo;
    }

    public TypeEnum getTypeEnum() {
        return null;
    }

    public int getType() {
        return type;
    }

    public byte getPayloadAsByte() {
        return findPayloadAsByte().orElse((byte) 0);
    }

    public Optional<Byte> findPayloadAsByte() {
        return findPayloadAsInteger()
                .filter(value -> (value >= 0 && value <= 0xFF))
                .map(Integer::byteValue);
    }

    public int getPayloadAsInteger() {
        return findPayloadAsInteger().orElse(0);
    }

    public Optional<Integer> findPayloadAsInteger() {
        try {
            return findPayloadAsString().map(Integer::parseInt);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public long getPayloadAsLong() {
        return findPayloadAsLong().orElse(0L);
    }

    public Optional<Long> findPayloadAsLong() {
        try {
            return findPayloadAsString().map(Long::parseLong);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public float getPayloadAsFloat() {
        return findPayloadAsFloat().orElse(Float.NaN);
    }

    public Optional<Float> findPayloadAsFloat() {
        try {
            return findPayloadAsString().map(Float::parseFloat);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public boolean getPayloadAsBoolean() {
        return findPayloadAsBoolean().orElse(false);
    }

    public Optional<Boolean> findPayloadAsBoolean() {
        return findPayloadAsString()
                .map(value -> VALUE_TRUE_AS_STRING.equals(value) || Boolean.parseBoolean(value));
    }

    public String getPayloadAsString() {
        return findPayloadAsString().orElse("");
    }

    public Optional<String> findPayloadAsString() {
        return Optional.ofNullable(payload);
    }

    public String getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "RawMessage{" +
                "nodeId=" + nodeId +
                ", childSensorId=" + childSensorId +
                ", command=" + command + " // " + SensorCommandType.byType(getCommand()) +
                ", echo/ack=" + echo +
                ", type=" + type +
                ", payload='" + payload + '\'' +
                '}';
    }

    public enum SensorCommandType {
        C_PRESENTATION(0), // Sent by a node when they present attached sensors. This is usually done in presentation() at startup.
        C_SET(1), // This message is sent from or to a sensor when a sensor value should be updated.
        C_REQ(2), // Requests a variable value (usually from an actuator destined for controller).
        C_INTERNAL(3), // Internal MySensors messages (also include common messages provided/generated by the library).
        C_STREAM(4),    //!< For firmware and other larger chunks of data that need to be divided into pieces.
        C_RESERVED_5(5),    //!< C_RESERVED_5
        C_RESERVED_6(6),    //!< C_RESERVED_6
        C_INVALID_7(7),    //!< C_INVALID_7
        //
        ;

        private final int type;

        SensorCommandType(int type) {
            this.type = type;
        }

        public static Optional<SensorCommandType> byType(int type) {
            for (SensorCommandType sensorType : values()) {
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
