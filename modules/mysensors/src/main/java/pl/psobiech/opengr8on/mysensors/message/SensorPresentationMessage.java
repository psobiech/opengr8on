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

public class SensorPresentationMessage extends Message {
    private final SensorCommandType commandEnum;

    private final SensorType typeEnum;

    SensorPresentationMessage(int nodeId, int childSensorId, SensorCommandType commandEnum, int ack, SensorType typeEnum, String payload) {
        super(nodeId, childSensorId, commandEnum.type(), ack, typeEnum.type(), payload);

        this.commandEnum = commandEnum;
        this.typeEnum    = typeEnum;
    }

    @Override
    public SensorCommandType getCommandEnum() {
        return commandEnum;
    }

    @Override
    public SensorType getTypeEnum() {
        return typeEnum;
    }

    @Override
    public String toString() {
        return "SensorPresentationMessage{" +
               "commandEnum=" + commandEnum +
               ", typeEnum=" + typeEnum +
               "} " + super.toString();
    }

    public enum SensorType implements TypeEnum {
        S_DOOR(0), // Door sensor, V_TRIPPED, V_ARMED
        S_MOTION(1), // Motion sensor, V_TRIPPED, V_ARMED
        S_SMOKE(2), // Smoke sensor, V_TRIPPED, V_ARMED
        S_BINARY(3), // Binary light or relay, V_STATUS, V_WATT
        S_LIGHT(3), // \deprecated Same as S_BINARY
        S_DIMMER(4), // Dimmable light or fan device, V_STATUS (on/off), V_PERCENTAGE (dimmer level 0-100), V_WATT
        S_COVER(5), // Blinds or window cover, V_UP, V_DOWN, V_STOP, V_PERCENTAGE (open/close to a percentage)
        S_TEMP(6), // Temperature sensor, V_TEMP
        S_HUM(7), // Humidity sensor, V_HUM
        S_BARO(8), // Barometer sensor, V_PRESSURE, V_FORECAST
        S_WIND(9), // Wind sensor, V_WIND, V_GUST
        S_RAIN(10), // Rain sensor, V_RAIN, V_RAINRATE
        S_UV(11), // Uv sensor, V_UV
        S_WEIGHT(12), // Personal scale sensor, V_WEIGHT, V_IMPEDANCE
        S_POWER(13), // Power meter, V_WATT, V_KWH, V_VAR, V_VA, V_POWER_FACTOR
        S_HEATER(14), // Header device, V_HVAC_SETPOINT_HEAT, V_HVAC_FLOW_STATE, V_TEMP
        S_DISTANCE(15), // Distance sensor, V_DISTANCE
        S_LIGHT_LEVEL(16), // Light level sensor, V_LIGHT_LEVEL (uncalibrated in percentage),  V_LEVEL (light level in lux)
        S_ARDUINO_NODE(17), // Used (internally) for presenting a non-repeating Arduino node
        S_ARDUINO_REPEATER_NODE(18), // Used (internally) for presenting a repeating Arduino node
        S_LOCK(19), // Lock device, V_LOCK_STATUS
        S_IR(20), // IR device, V_IR_SEND, V_IR_RECEIVE
        S_WATER(21), // Water meter, V_FLOW, V_VOLUME
        S_AIR_QUALITY(22), // Air quality sensor, V_LEVEL
        S_CUSTOM(23), // Custom sensor
        S_DUST(24), // Dust sensor, V_LEVEL
        S_SCENE_CONTROLLER(25), // Scene controller device, V_SCENE_ON, V_SCENE_OFF.
        S_RGB_LIGHT(26), // RGB light. Send color component data using V_RGB. Also supports V_WATT
        S_RGBW_LIGHT(27), // RGB light with an additional White component. Send data using V_RGBW. Also supports V_WATT
        S_COLOR_SENSOR(28), // Color sensor, send color information using V_RGB
        S_HVAC(29), // Thermostat/HVAC device. V_HVAC_SETPOINT_HEAT, V_HVAC_SETPOINT_COLD, V_HVAC_FLOW_STATE, V_HVAC_FLOW_MODE, V_TEMP
        S_MULTIMETER(30), // Multimeter device, V_VOLTAGE, V_CURRENT, V_IMPEDANCE
        S_SPRINKLER(31), // Sprinkler, V_STATUS (turn on/off), V_TRIPPED (if fire detecting device)
        S_WATER_LEAK(32), // Water leak sensor, V_TRIPPED, V_ARMED
        S_SOUND(33), // Sound sensor, V_TRIPPED, V_ARMED, V_LEVEL (sound level in dB)
        S_VIBRATION(34), // Vibration sensor, V_TRIPPED, V_ARMED, V_LEVEL (vibration in Hz)
        S_MOISTURE(35), // Moisture sensor, V_TRIPPED, V_ARMED, V_LEVEL (water content or moisture in percentage?)
        S_INFO(36), // LCD text device / Simple information device on controller, V_TEXT
        S_GAS(37), // Gas meter, V_FLOW, V_VOLUME
        S_GPS(38), // GPS Sensor, V_POSITION
        S_WATER_QUALITY(39), // V_TEMP, V_PH, V_ORP, V_EC, V_STATUS
        //
        ;

        private final int type;

        SensorType(int type) {
            this.type = type;
        }

        public static Optional<SensorType> byType(int type) {
            for (SensorType sensorType : values()) {
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
