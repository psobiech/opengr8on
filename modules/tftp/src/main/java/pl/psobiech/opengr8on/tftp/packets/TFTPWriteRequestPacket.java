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
import pl.psobiech.opengr8on.tftp.TFTPTransferMode;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.util.SocketUtil.Payload;

import java.net.InetAddress;

public class TFTPWriteRequestPacket extends TFTPRequestPacket {
    public TFTPWriteRequestPacket(Payload payload) throws TFTPPacketException {
        super(TFTPPacketType.WRITE_REQUEST, payload);
    }

    public TFTPWriteRequestPacket(InetAddress destination, int port, String fileName, TFTPTransferMode mode) {
        super(destination, port, TFTPPacketType.WRITE_REQUEST, fileName, mode);
    }

    @Override
    public String toString() {
        return super.toString() + " WRQ " + getFileName() + " " + getMode();
    }
}
