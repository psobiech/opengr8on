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

package pl.psobiech.opengr8on.tftp.transfer;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.tftp.TFTP;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.tftp.packets.TFTPErrorPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPPacket;

public abstract class TFTPTransfer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TFTPTransfer.class);

    public static final int DEFAULT_RETRIES = 3;

    protected int maxRetries = DEFAULT_RETRIES;

    private void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public abstract void execute(TFTP tftp) throws IOException, TFTPPacketException;

    protected TFTPPacket readResponsePacket(
        TFTP tftp,
        boolean allowAllOrigins,
        InetAddress requestAddress, int requestPort,
        TFTPPacket lastPacket
    ) throws IOException, TFTPPacketException {
        int retries = maxRetries;

        while (!Thread.interrupted()) {
            try {
                final TFTPPacket responsePacket = tftp.receive();
                final InetAddress responseAddress = responsePacket.getAddress();
                final int responsePort = responsePacket.getPort();
                if (!allowAllOrigins && (
                    !responseAddress.equals(requestAddress)
                    || !(responsePort == requestPort)
                )) {
                    final TFTPPacketException packetException = new TFTPPacketException(TFTPErrorPacket.UNKNOWN_TID, "Unexpected Host or Port");
                    LOGGER.debug("TFTP Server ignoring message from unexpected source.", packetException);

                    tftp.send(packetException.asError(responseAddress, responsePort));

                    continue;
                }

                return responsePacket;
            } catch (SocketTimeoutException e) {
                if (retries-- < 0) {
                    throw e;
                }

                // didn't get an ack for this data. need to resend it.
                tftp.send(lastPacket);
            }
        }

        throw new InterruptedIOException();
    }
}
