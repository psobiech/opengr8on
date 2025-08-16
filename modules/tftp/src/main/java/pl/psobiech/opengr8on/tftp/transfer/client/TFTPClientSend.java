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

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.tftp.TFTP;
import pl.psobiech.opengr8on.tftp.TFTPTransferMode;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPException;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.tftp.packets.TFTPAcknowledgementPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPErrorPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPErrorType;
import pl.psobiech.opengr8on.tftp.packets.TFTPPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPWriteRequestPacket;
import pl.psobiech.opengr8on.tftp.transfer.TFTPSendingTransfer;

public class TFTPClientSend extends TFTPSendingTransfer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TFTPClientSend.class);

    private final TFTPWriteRequestPacket tftpPacket;

    private final Path file;

    public TFTPClientSend(InetAddress host, int port, TFTPTransferMode mode, String fileName, Path file) {
        this.tftpPacket = new TFTPWriteRequestPacket(host, port, fileName, mode);

        this.file = file;
    }

    @Override
    public void execute(TFTP tftp) throws IOException, TFTPPacketException {
        tftp.send(tftpPacket);

        InetAddress host = tftpPacket.getAddress();
        int port = tftpPacket.getPort();

        final TFTPPacket responsePacket = readResponsePacket(tftp, true, host, port, tftpPacket);
        host = responsePacket.getAddress();
        port = responsePacket.getPort();

        if (!(responsePacket instanceof TFTPAcknowledgementPacket)) {
            if (responsePacket instanceof TFTPErrorPacket errorPacket) {
                throw new TFTPException(
                        errorPacket.getError(),
                        "Unexpected response from tftp client during transfer (" + responsePacket + "). Transfer aborted."
                );
            }

            throw new TFTPException(
                    TFTPErrorType.UNDEFINED,
                    "Unexpected response from tftp client during transfer (" + responsePacket + "). Transfer aborted."
            );
        }

        outgoingTransfer(tftp, false, file, tftpPacket.getMode(), host, port);
    }
}
