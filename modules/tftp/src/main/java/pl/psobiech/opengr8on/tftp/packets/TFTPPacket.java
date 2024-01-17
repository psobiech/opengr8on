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

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import pl.psobiech.opengr8on.tftp.TFTPPacketType;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.util.SocketUtil.Payload;

public abstract class TFTPPacket {
    protected static final int OPERATOR_TYPE_OFFSET = 1;

    public static final int MAX_DATA_LENGTH = 512;

    final TFTPPacketType type;

    private int port;

    private InetAddress address;

    TFTPPacket(TFTPPacketType type, InetAddress address, int port) {
        this.type    = type;
        this.address = address;
        this.port    = port;
    }

    public static TFTPPacket newTFTPPacket(Payload payload) throws TFTPPacketException {
        return TFTPPacketType.ofPacketType(payload.buffer()[OPERATOR_TYPE_OFFSET])
                             .parse(payload);
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public TFTPPacketType getType() {
        return type;
    }

    public abstract DatagramPacket newDatagram(byte[] data);

    public void setAddress(InetAddress address) {
        this.address = address;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public static String readNullTerminatedString(byte[] buffer, int from, int to) {
        return new String(
            readNullTerminated(buffer, from, to), StandardCharsets.US_ASCII
        );
    }

    public static byte[] readNullTerminated(byte[] buffer, int from, int to) {
        int nullTerminator = -1;
        for (int i = from; i < to; i++) {
            if (buffer[i] == 0) {
                nullTerminator = i;
                break;
            }
        }

        if (nullTerminator < 0) {
            return Arrays.copyOfRange(buffer, from, to);
        }

        return Arrays.copyOfRange(buffer, from, nullTerminator);
    }

    public static int writeNullTerminatedString(String value, byte[] buffer, int offset) {
        final byte[] valueAsBytes = value.getBytes(StandardCharsets.US_ASCII);

        System.arraycopy(valueAsBytes, 0, buffer, offset, valueAsBytes.length);
        buffer[offset + valueAsBytes.length] = 0;

        return valueAsBytes.length + 1;
    }

    public static int readInt(byte[] buffer, int offset) {
        return asInt(buffer[offset], buffer[offset + 1]);
    }

    public static void writeInt(int value, byte[] buffer, int offset) {
        buffer[offset]     = highNibble(value);
        buffer[offset + 1] = lowNibble(value);
    }

    public static byte highNibble(int value) {
        return (byte) ((value & 0xFFFF) >> (Integer.BYTES * 2));
    }

    public static byte lowNibble(int value) {
        return (byte) (value & 0xFF);
    }

    public static int asInt(byte highNibble, byte lowNibble) {
        return asInt(highNibble) << (Integer.BYTES * 2) | asInt(lowNibble);
    }

    private static int asInt(byte value) {
        return value & 0xFF;
    }

    @Override
    public String toString() {
        return address + " " + port + " " + type;
    }
}
