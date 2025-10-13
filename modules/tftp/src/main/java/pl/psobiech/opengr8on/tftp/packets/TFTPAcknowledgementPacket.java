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

import pl.psobiech.opengr8on.tftp.TFTPPacketType;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.util.SocketUtil.Payload;

import java.net.DatagramPacket;
import java.net.InetAddress;

/**
 * Block acknowledgement packet
 */
public class TFTPAcknowledgementPacket extends TFTPBaseBlockPacket {
    private static final int PACKET_SIZE = HEADER_SIZE;

    public TFTPAcknowledgementPacket(Payload datagram) throws TFTPPacketException {
        super(TFTPPacketType.ACKNOWLEDGEMENT, datagram.address(), datagram.port(), getBlockNumber(datagram));

        final byte[] buffer = datagram.buffer();
        if (getType().packetType() != buffer[OPERATOR_TYPE_OFFSET]) {
            throw new TFTPPacketException("TFTP operator code does not match type.");
        }
    }

    public TFTPAcknowledgementPacket(InetAddress destination, int port, int blockNumber) {
        super(TFTPPacketType.ACKNOWLEDGEMENT, destination, port, blockNumber);
    }

    @Override
    public DatagramPacket newDatagram(byte[] data) {
        writeHeader(data);

        return new DatagramPacket(data, 0, PACKET_SIZE, getAddress(), getPort());
    }

    @Override
    public String toString() {
        return super.toString() + " ACK " + getBlockNumber();
    }
}
