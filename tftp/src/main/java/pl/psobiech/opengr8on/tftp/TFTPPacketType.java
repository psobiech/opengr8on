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

import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.tftp.packets.TFTPAckPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPDataPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPErrorPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPReadRequestPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPWriteRequestPacket;
import pl.psobiech.opengr8on.util.SocketUtil.Payload;

public enum TFTPPacketType {
    UNKNOWN(
        0,
        payload -> {
            throw new TFTPPacketException("Bad packet. Invalid TFTP operator code.");
        }
    ),
    READ_REQUEST(1, TFTPReadRequestPacket::new),
    WRITE_REQUEST(2, TFTPWriteRequestPacket::new),
    DATA(3, TFTPDataPacket::new),
    ACKNOWLEDGEMENT(4, TFTPAckPacket::new),
    ERROR(5, TFTPErrorPacket::new),
    //
    ;

    private final byte packetType;

    private final Parser parser;

    TFTPPacketType(int packetType, Parser parser) {
        if (packetType < 0 || packetType > 0xFF) {
            throw new IllegalArgumentException();
        }

        this.packetType = (byte) (packetType & 0xFF);
        this.parser     = parser;
    }

    public byte packetType() {
        return packetType;
    }

    public TFTPPacket parse(Payload payload) throws TFTPPacketException {
        return parser.parse(payload);
    }

    public static TFTPPacketType ofPacketType(byte packetType) {
        for (TFTPPacketType value : values()) {
            if (value.packetType() == packetType) {
                return value;
            }
        }

        return UNKNOWN;
    }

    @FunctionalInterface
    private interface Parser {
        TFTPPacket parse(Payload payload) throws TFTPPacketException;
    }
}
