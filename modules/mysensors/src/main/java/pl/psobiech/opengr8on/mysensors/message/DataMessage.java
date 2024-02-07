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

public class DataMessage extends Message {
    private final SensorCommandType commandEnum;

    private final SensorDataType typeEnum;

    DataMessage(int nodeId, int childSensorId, SensorCommandType commandEnum, int ack, SensorDataType typeEnum, String payload) {
        super(nodeId, childSensorId, commandEnum.type(), ack, typeEnum.type(), payload);

        this.commandEnum = commandEnum;
        this.typeEnum    = typeEnum;
    }

    @Override
    public SensorCommandType getCommandEnum() {
        return commandEnum;
    }

    @Override
    public SensorDataType getTypeEnum() {
        return typeEnum;
    }

    @Override
    public String toString() {
        return "DataMessage{" +
               "commandEnum=" + commandEnum +
               ", typeEnum=" + typeEnum +
               "} " + super.toString();
    }

    public enum SensorDataType implements TypeEnum {
        V_TEMP(0), // S_TEMP. Temperature S_TEMP, S_HEATER, S_HVAC
        V_HUM(1), // S_HUM. Humidity
        V_STATUS(2), // S_BINARY, S_DIMMER, S_SPRINKLER, S_HVAC, S_HEATER. Used for setting/reporting binary (on/off) status. 1=on, 0=off
        V_LIGHT(2), // \deprecated Same as V_STATUS
        V_PERCENTAGE(3), // S_DIMMER. Used for sending a percentage value 0-100 (%).
        V_DIMMER(3), // \deprecated Same as V_PERCENTAGE
        V_PRESSURE(4), // S_BARO. Atmospheric Pressure
        V_FORECAST(5), // S_BARO. Whether forecast. string of "stable", "sunny", "cloudy", "unstable", "thunderstorm" or "unknown"
        V_RAIN(6), // S_RAIN. Amount of rain
        V_RAINRATE(7), // S_RAIN. Rate of rain
        V_WIND(8), // S_WIND. Wind speed
        V_GUST(9), // S_WIND. Gust
        V_DIRECTION(10), // S_WIND. Wind direction 0-360 (degrees)
        V_UV(11), // S_UV. UV light level
        V_WEIGHT(12), // S_WEIGHT. Weight(for scales etc)
        V_DISTANCE(13), // S_DISTANCE. Distance
        V_IMPEDANCE(14), // S_MULTIMETER, S_WEIGHT. Impedance value
        V_ARMED(15), // S_DOOR, S_MOTION, S_SMOKE, S_SPRINKLER. Armed status of a security sensor. 1 = Armed, 0 = Bypassed
        V_TRIPPED(16), // S_DOOR, S_MOTION, S_SMOKE, S_SPRINKLER, S_WATER_LEAK, S_SOUND, S_VIBRATION, S_MOISTURE. Tripped status of a security sensor. 1 = Tripped, 0
        V_WATT(17), // S_POWER, S_BINARY, S_DIMMER, S_RGB_LIGHT, S_RGBW_LIGHT. Watt value for power meters
        V_KWH(18), // S_POWER. Accumulated number of KWH for a power meter
        V_SCENE_ON(19), // S_SCENE_CONTROLLER. Turn on a scene
        V_SCENE_OFF(20), // S_SCENE_CONTROLLER. Turn of a scene
        V_HVAC_FLOW_STATE(21), // S_HEATER, S_HVAC. HVAC flow state ("Off", "HeatOn", "CoolOn", or "AutoChangeOver")
        V_HEATER(21), // \deprecated Same as V_HVAC_FLOW_STATE
        V_HVAC_SPEED(22), // S_HVAC, S_HEATER. HVAC/Heater fan speed ("Min", "Normal", "Max", "Auto")
        V_LIGHT_LEVEL(23), // S_LIGHT_LEVEL. Uncalibrated light level. 0-100%. Use V_LEVEL for light level in lux
        V_VAR1(24), // VAR1
        V_VAR2(25), // VAR2
        V_VAR3(26), // VAR3
        V_VAR4(27), // VAR4
        V_VAR5(28), // VAR5
        V_UP(29), // S_COVER. Window covering. Up
        V_DOWN(30), // S_COVER. Window covering. Down
        V_STOP(31), // S_COVER. Window covering. Stop
        V_IR_SEND(32), // S_IR. Send out an IR-command
        V_IR_RECEIVE(33), // S_IR. This message contains a received IR-command
        V_FLOW(34), // S_WATER. Flow of water (in meter)
        V_VOLUME(35), // S_WATER. Water volume
        V_LOCK_STATUS(36), // S_LOCK. Set or get lock status. 1=Locked, 0=Unlocked
        V_LEVEL(37), // S_DUST, S_AIR_QUALITY, S_SOUND (dB), S_VIBRATION (hz), S_LIGHT_LEVEL (lux)
        V_VOLTAGE(38), // S_MULTIMETER
        V_CURRENT(39), // S_MULTIMETER
        V_RGB(40), // S_RGB_LIGHT, S_COLOR_SENSOR. Sent as ASCII hex: RRGGBB (RR=red, GG=green, BB=blue component)
        V_RGBW(41), // S_RGBW_LIGHT. Sent as ASCII hex: RRGGBBWW (WW=white component)
        V_ID(42), // Used for sending in sensors hardware ids (i.e. OneWire DS1820b).
        V_UNIT_PREFIX(43), // Allows sensors to send in a string representing the unit prefix to be displayed in GUI, not parsed by controller! E.g. cm, m, km, inch.
        V_HVAC_SETPOINT_COOL(44), // S_HVAC. HVAC cool setpoint (Integer between 0-100)
        V_HVAC_SETPOINT_HEAT(45), // S_HEATER, S_HVAC. HVAC/Heater setpoint (Integer between 0-100)
        V_HVAC_FLOW_MODE(46), // S_HVAC. Flow mode for HVAC ("Auto", "ContinuousOn", "PeriodicOn")
        V_TEXT(47), // S_INFO. Text message to display on LCD or controller device
        V_CUSTOM(48), // Custom messages used for controller/inter node specific commands, preferably using S_CUSTOM device type.
        V_POSITION(49), // GPS position and altitude. Payload: latitude;longitude;altitude(m). E.g. "55.722526;13.017972;18"
        V_IR_RECORD(50), // Record IR codes S_IR for playback
        V_PH(51), // S_WATER_QUALITY, water PH
        V_ORP(52), // S_WATER_QUALITY, water ORP : redox potential in mV
        V_EC(53), // S_WATER_QUALITY, water electric conductivity μS/cm (microSiemens/cm)
        V_VAR(54), // S_POWER, Reactive power: volt-ampere reactive (var)
        V_VA(55), // S_POWER, Apparent power: volt-ampere (VA)
        V_POWER_FACTOR(56), // S_POWER, Ratio of real power to apparent power: floating point value in the range [-1,..,1]
        V_MULTI_MESSAGE(57), // Special type, multiple sensors in one message
        //
        ;

        private final int type;

        SensorDataType(int type) {
            this.type = type;
        }

        public static Optional<SensorDataType> byType(int type) {
            for (SensorDataType sensorType : values()) {
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
