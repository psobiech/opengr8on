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

import java.net.InetAddress;
import java.nio.file.Path;

import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.tftp.packets.TFTPPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPReadRequestPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPRequestPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPWriteRequestPacket;
import pl.psobiech.opengr8on.tftp.transfer.TFTPTransfer;
import pl.psobiech.opengr8on.tftp.transfer.server.TFTPServerReceive;
import pl.psobiech.opengr8on.tftp.transfer.server.TFTPServerSend;

public enum TFTPTransferType {
    SERVER_READ_REQUEST(TFTPReadRequestPacket.class, TFTPServerSend::new),
    SERVER_WRITE_REQUEST(TFTPWriteRequestPacket.class, TFTPServerReceive::new),
    //
    ;

    private final Class<? extends TFTPPacket> packetClass;

    private final Creator creator;

    <T extends TFTPRequestPacket> TFTPTransferType(Class<T> packetClass, Creator creator) {
        this.packetClass = packetClass;
        this.creator = creator;
    }

    public TFTPTransfer create(TFTPRequestPacket packet, Path path) throws TFTPPacketException {
        return creator.create(packet, path);
    }

    public TFTPTransfer create(InetAddress host, int port, TFTPTransferMode mode, Path path, String location) throws TFTPPacketException {
        return creator.create(host, port, mode, path, location);
    }

    public static TFTPTransferType ofServerPacket(TFTPPacket packet) throws TFTPPacketException {
        for (TFTPTransferType value : values()) {
            if (value.packetClass().isInstance(packet)) {
                return value;
            }
        }

        throw new TFTPPacketException("Unexpected TFTP packet: " + packet.getType());
    }

    private Class<? extends TFTPPacket> packetClass() {
        return packetClass;
    }

    @FunctionalInterface
    private interface Creator {
        default TFTPTransfer create(TFTPRequestPacket packet, Path path) throws TFTPPacketException {
            return create(
                    packet.getAddress(), packet.getPort(),
                    packet.getMode(),
                    path, packet.getFileName()
            );
        }

        TFTPTransfer create(InetAddress host, int port, TFTPTransferMode mode, Path path, String location) throws TFTPPacketException;
    }
}
