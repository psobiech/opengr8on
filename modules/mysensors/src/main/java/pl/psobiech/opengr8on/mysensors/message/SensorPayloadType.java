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

public enum SensorPayloadType {
    P_STRING(0), // Payload type is string
    P_BYTE(1), // Payload type is byte
    P_INT16(2), // Payload type is INT16
    P_UINT16(3), // Payload type is UINT16
    P_LONG32(4), // Payload type is INT32
    P_ULONG32(5), // Payload type is UINT32
    P_CUSTOM(6), // Payload type is binary
    P_FLOAT32(7), // Payload type is float32
    //
    ;

    private final int type;

    SensorPayloadType(int type) {
        this.type = type;
    }

    static Optional<SensorPayloadType> byType(int type) {
        for (SensorPayloadType sensorType : values()) {
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
