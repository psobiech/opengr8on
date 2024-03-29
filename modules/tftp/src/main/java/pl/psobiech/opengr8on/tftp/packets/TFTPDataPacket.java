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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.InetAddress;

import pl.psobiech.opengr8on.tftp.TFTPPacketType;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.util.SocketUtil.Payload;

public class TFTPDataPacket extends TFTPBaseBlockPacket {
    private final int length;

    private final int offset;

    private final byte[] buffer;

    public TFTPDataPacket(Payload payload) throws TFTPPacketException {
        super(TFTPPacketType.DATA, payload.address(), payload.port(), getBlockNumber(payload));

        this.buffer = payload.buffer();
        if (getType().packetType() != this.buffer[OPERATOR_TYPE_OFFSET]) {
            throw new TFTPPacketException("TFTP operator code does not match type.");
        }

        this.offset = HEADER_SIZE;
        this.length = Math.min(buffer.length - HEADER_SIZE, MAX_DATA_LENGTH);
    }

    public TFTPDataPacket(InetAddress destination, int port, int blockNumber, byte[] buffer, int offset, int length) {
        super(TFTPPacketType.DATA, destination, port, blockNumber);

        this.buffer = buffer;
        this.offset = offset;

        this.length = Math.min(length, MAX_DATA_LENGTH);
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public int getDataLength() {
        return length;
    }

    public int getDataOffset() {
        return offset;
    }

    @Override
    public DatagramPacket newDatagram(byte[] buffer) {
        if (buffer == this.buffer) {
            throw new UncheckedIOException(new IOException("Unexpected buffer passed to method"));
        }

        writeHeader(buffer);
        System.arraycopy(this.buffer, offset, buffer, HEADER_SIZE, length);

        return new DatagramPacket(buffer, 0, HEADER_SIZE + length, getAddress(), getPort());
    }

    @Override
    public String toString() {
        return super.toString() + " DATA " + getBlockNumber() + " " + length;
    }
}
