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

import java.net.InetAddress;

import pl.psobiech.opengr8on.tftp.TFTPPacketType;
import pl.psobiech.opengr8on.util.SocketUtil.Payload;

public abstract class TFTPBlockPacket extends TFTPPacket {
    protected static final int HEADER_SIZE = 4;

    protected static final int BLOCK_NUMBER_OFFSET = 2;

    private final int blockNumber;

    protected TFTPBlockPacket(TFTPPacketType type, InetAddress address, int port, int blockNumber) {
        super(type, address, port);

        this.blockNumber = blockNumber;
    }

    protected static int getBlockNumber(Payload payload) {
        final byte[] buffer = payload.buffer();

        return readInt(buffer, BLOCK_NUMBER_OFFSET);
    }

    protected void writeHeader(byte[] buffer) {
        buffer[0]                    = 0;
        buffer[OPERATOR_TYPE_OFFSET] = type.packetType();
        writeInt(getBlockNumber(), buffer, BLOCK_NUMBER_OFFSET);
    }

    public int getBlockNumber() {
        return blockNumber;
    }
}
