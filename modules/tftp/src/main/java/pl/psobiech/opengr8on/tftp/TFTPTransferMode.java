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

package pl.psobiech.opengr8on.tftp;

import java.nio.charset.StandardCharsets;

import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;

public enum TFTPTransferMode {
    /**
     * The netascii/ascii transfer mode
     */
    NETASCII(0, "netascii"),
    /**
     * The binary/octet transfer mode
     */
    OCTET(1, "octet"),
    //
    ;

    private final int code;

    private final String value;

    TFTPTransferMode(int code, String value) {
        this.code  = code;
        this.value = value;

    }

    public static TFTPTransferMode ofMode(String mode) throws TFTPPacketException {
        for (TFTPTransferMode value : values()) {
            if (value.value().equalsIgnoreCase(mode)) {
                return value;
            }
        }

        throw new TFTPPacketException("Unrecognized TFTP transfer mode: " + mode);
    }

    public int code() {
        return code;
    }

    public byte[] valueAsBytes() {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    public String value() {
        return value;
    }
}
