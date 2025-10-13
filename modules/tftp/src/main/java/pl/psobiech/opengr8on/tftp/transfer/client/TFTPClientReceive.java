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

package pl.psobiech.opengr8on.tftp.transfer.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.tftp.TFTP;
import pl.psobiech.opengr8on.tftp.TFTPTransferMode;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.tftp.packets.TFTPReadRequestPacket;
import pl.psobiech.opengr8on.tftp.transfer.TFTPReceivingTransfer;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;

public class TFTPClientReceive extends TFTPReceivingTransfer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TFTPClientReceive.class);

    private final TFTPReadRequestPacket tftpPacket;

    private final Path file;

    public TFTPClientReceive(InetAddress host, int port, TFTPTransferMode mode, String fileName, Path file) {
        this.tftpPacket = new TFTPReadRequestPacket(host, port, fileName, mode);

        this.file = file;
    }

    @Override
    public void execute(TFTP tftp) throws IOException, TFTPPacketException {
        tftp.send(tftpPacket);

        incomingTransfer(tftp, false, tftpPacket.getMode(), tftpPacket.getAddress(), tftpPacket.getPort(), tftpPacket, file);
    }
}
