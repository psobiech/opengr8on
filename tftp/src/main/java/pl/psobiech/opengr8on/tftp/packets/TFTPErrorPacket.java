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

import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;

public class TFTPErrorPacket extends TFTPPacket {
    private static final int HEADER_SIZE = 4;

    private static final int ERROR_OFFSET = 2;

    /**
     * The undefined error code according to RFC 783.
     */
    public static final int UNDEFINED = 0;

    /**
     * The file not found error code according to RFC 783.
     */
    public static final int FILE_NOT_FOUND = 1;

    /**
     * The access violation error code according to RFC 783.
     */
    public static final int ACCESS_VIOLATION = 2;

    /**
     * The disk full error code according to RFC 783.
     */
    public static final int OUT_OF_SPACE = 3;

    /**
     * The illegal TFTP operation error code according to RFC 783.
     */
    public static final int ILLEGAL_OPERATION = 4;

    /**
     * The unknown transfer id error code according to RFC 783.
     */
    public static final int UNKNOWN_TID = 5;

    /**
     * The file already exists error code according to RFC 783.
     */
    public static final int FILE_EXISTS = 6;

    private final int error;

    private final String message;

    TFTPErrorPacket(DatagramPacket datagram) throws TFTPPacketException {
        super(ERROR, datagram.getAddress(), datagram.getPort());

        final int length = datagram.getLength();
        if (length < HEADER_SIZE + 1) {
            throw new TFTPPacketException("Bad error packet. No message.");
        }

        final byte[] data = datagram.getData();
        if (getType() != data[OPERATOR_TYPE_OFFSET]) {
            throw new TFTPPacketException("TFTP operator code does not match type.");
        }

        error   = readInt(data, ERROR_OFFSET);
        message = readNullTerminatedString(data, HEADER_SIZE, length);
    }

    public TFTPErrorPacket(InetAddress destination, int port, int error, String message) {
        super(ERROR, destination, port);

        this.error   = error;
        this.message = message;
    }

    public int getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public DatagramPacket newDatagram() {
        final byte[] messageAsBytes = message.getBytes(StandardCharsets.US_ASCII);
        final byte[] data = new byte[HEADER_SIZE + messageAsBytes.length + 1];

        return newDatagram(data);
    }

    @Override
    public DatagramPacket newDatagram(byte[] data) {
        writeHeader(data);

        final int messageLength = writeNullTerminatedString(message, data, HEADER_SIZE);

        return new DatagramPacket(data, 0, HEADER_SIZE + messageLength, getAddress(), getPort());
    }

    private void writeHeader(byte[] data) {
        data[0]                    = 0;
        data[OPERATOR_TYPE_OFFSET] = type;
        writeInt(error, data, ERROR_OFFSET);
    }

    @Override
    public String toString() {
        return super.toString() + " ERR " + error + " " + message;
    }
}
