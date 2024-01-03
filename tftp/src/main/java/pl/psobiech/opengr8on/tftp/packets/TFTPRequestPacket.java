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
import java.util.List;

import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;

public abstract class TFTPRequestPacket extends TFTPPacket {
    public static final List<String> MODES = List.of("netascii", "octet");

    private static final int HEADER_SIZE = 2;

    private static final int FILE_NAME_OFFSET = 2;

    private final int mode;

    private final String fileName;

    TFTPRequestPacket(InetAddress destination, int port, byte type, String fileName, int mode) {
        super(type, destination, port);

        this.fileName = fileName;
        this.mode     = mode;
    }

    TFTPRequestPacket(byte type, DatagramPacket datagram) throws TFTPPacketException {
        super(type, datagram.getAddress(), datagram.getPort());

        final byte[] data = datagram.getData();
        if (getType() != data[OPERATOR_TYPE_OFFSET]) {
            throw new TFTPPacketException("TFTP operator code does not match type.");
        }

        final int length = datagram.getLength();

        final byte[] fileNameAsBytes = readNullTerminated(data, FILE_NAME_OFFSET, length);
        this.fileName = new String(fileNameAsBytes, StandardCharsets.US_ASCII);

        if (2 + fileNameAsBytes.length >= length) {
            throw new TFTPPacketException("Bad file name and mode format.");
        }

        final String modeAsString = readNullTerminatedString(data, FILE_NAME_OFFSET + fileNameAsBytes.length + 1, length)
            .toLowerCase(java.util.Locale.ENGLISH);

        this.mode = MODES.indexOf(modeAsString);
        if (this.mode < 0) {
            throw new TFTPPacketException("Unrecognized TFTP transfer mode: " + modeAsString);
        }
    }

    public String getFileName() {
        return fileName;
    }

    public int getMode() {
        return mode;
    }

    @Override
    public DatagramPacket newDatagram() {
        final byte[] fileNameAsBytes = fileName.getBytes(StandardCharsets.US_ASCII);
        final byte[] modeAsBytes = MODES.get(mode).getBytes(StandardCharsets.US_ASCII);

        final byte[] data = new byte[HEADER_SIZE + fileNameAsBytes.length + 1 + modeAsBytes.length + 1];

        return newDatagram(data);
    }

    @Override
    public DatagramPacket newDatagram(byte[] data) {
        data[0] = 0;
        data[OPERATOR_TYPE_OFFSET] = type;

        final int fileNameLength = writeNullTerminatedString(fileName, data, FILE_NAME_OFFSET);
        final int modeLength = writeNullTerminatedString(MODES.get(mode), data, HEADER_SIZE + fileNameLength);

        return new DatagramPacket(
            data, 0, HEADER_SIZE + fileNameLength + modeLength,
            getAddress(), getPort()
        );
    }
}
