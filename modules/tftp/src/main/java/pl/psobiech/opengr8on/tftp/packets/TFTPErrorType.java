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

package pl.psobiech.opengr8on.tftp.packets;

/**
 * Error codes according to RFC 783.
 */
public enum TFTPErrorType {
    UNDEFINED(0),
    FILE_NOT_FOUND(1),
    ACCESS_VIOLATION(2),
    OUT_OF_SPACE(3),
    ILLEGAL_OPERATION(4),
    UNKNOWN_TID(5),
    FILE_EXISTS(6),
    //
    ;

    private final int errorCode;

    TFTPErrorType(long errorCode) {
        if (errorCode < 0 || errorCode > 0xFFFFFFFFL) {
            throw new IllegalArgumentException();
        }

        this.errorCode = (int) (errorCode & 0xFFFFFFFFL);
    }

    public static TFTPErrorType ofErrorCode(int errorCode) {
        for (TFTPErrorType value : values()) {
            if (value.errorCode() == errorCode) {
                return value;
            }
        }

        return UNDEFINED;
    }

    public int errorCode() {
        return errorCode;
    }
}
