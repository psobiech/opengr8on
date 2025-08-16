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
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.tftp.TFTP;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPException;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.tftp.packets.TFTPErrorType;
import pl.psobiech.opengr8on.tftp.packets.TFTPPacket;

public abstract class TFTPTransfer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TFTPTransfer.class);

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    public static final int DEFAULT_RETRIES = 3;

    protected int maxRetries = DEFAULT_RETRIES;

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public abstract void execute(TFTP tftp) throws IOException, TFTPPacketException;

    protected TFTPPacket readResponsePacket(
            TFTP tftp,
            boolean allowAllOrigins,
            InetAddress requestAddress, int requestPort,
            TFTPPacket lastPacket
    ) throws IOException, TFTPPacketException {
        int retires = 3;

        do {
            final Optional<TFTPPacket> responsePacketOptional = tftp.receive(DEFAULT_TIMEOUT);
            if (responsePacketOptional.isEmpty()) {
                // didn't get an ack for this data. need to resend it.
                tftp.send(lastPacket);

                continue;
            }

            final TFTPPacket responsePacket = responsePacketOptional.get();
            final InetAddress responseAddress = responsePacket.getAddress();
            final int responsePort = responsePacket.getPort();
            if (!allowAllOrigins
                    && (
                    !responseAddress.equals(requestAddress) || !(responsePort == requestPort)
            )) {
                final TFTPPacketException packetException = new TFTPPacketException(TFTPErrorType.UNKNOWN_TID, "Unexpected Host or Port");
                LOGGER.debug("TFTP Server ignoring message from unexpected source.", packetException);

                tftp.send(packetException.asError(responseAddress, responsePort));

                continue;
            }

            return responsePacket;
        } while (!Thread.interrupted() && --retires > 0);

        if (retires == 0) {
            throw new TFTPException(TFTPErrorType.UNDEFINED, "Retries exceeded, transfer aborted");
        }

        throw new InterruptedIOException();
    }
}
