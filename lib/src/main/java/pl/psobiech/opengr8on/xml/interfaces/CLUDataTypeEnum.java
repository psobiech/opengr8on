/*
 * OpenGr8on, open source extensions to systems based on Grenton devices
 * Copyright (C) 2023 Piotr Sobiech
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pl.psobiech.opengr8on.xml.interfaces;

import pl.psobiech.opengr8on.exceptions.UnexpectedException;

public enum CLUDataTypeEnum {
    VOID("void"),
    NUMBER("num"),
    STRING("str"),
    BOOLEAN("confirmation"),
    TIMESTAMP("timestamp"),
    //
    ;

    private final String type;

    CLUDataTypeEnum(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public static CLUDataTypeEnum of(String type) {
        for (CLUDataTypeEnum value : values()) {
            if (value.getType().equals(type)) {
                return value;
            }
        }

        throw new UnexpectedException("Unsupported data type: " + type);
    }
}
