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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.commons.net.io.FromNetASCIIOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.tftp.TFTP;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.tftp.packets.TFTPAckPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPDataPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPErrorPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPRequestPacket;

public abstract class TFTPReceivingTransfer extends TFTPTransfer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TFTPReceivingTransfer.class);

    protected void incomingTransfer(
        TFTP tftp, boolean server,
        int mode,
        InetAddress requestAddress, int requestPort,
        Path targetPath
    ) throws IOException, TFTPPacketException {
        final Path temporaryPath = Files.createTempFile(null, null);

        final OutputStream outputStream;
        try {
            outputStream = createOutputStream(temporaryPath, mode);
        } catch (IOException e) {
            Files.deleteIfExists(temporaryPath);

            final TFTPPacketException packetException = new TFTPPacketException(TFTPErrorPacket.UNDEFINED, e.getMessage(), e);
            tftp.send(packetException.asError(requestAddress, requestPort));

            throw packetException;
        }

        TFTPAckPacket lastSentAck = new TFTPAckPacket(requestAddress, requestPort, 0);
        try (outputStream) {
            if (server) {
                tftp.send(lastSentAck);
            }

            boolean firstPacket = !server;
            int acknowledgedBlock = 0;
            while (!Thread.interrupted()) {
                final TFTPPacket responsePacket = readResponsePacket(tftp, firstPacket, requestAddress, requestPort, lastSentAck);
                if (firstPacket) {
                    firstPacket = false;

                    requestAddress = responsePacket.getAddress();
                    requestPort    = responsePacket.getPort();
                }

                if (server && acknowledgedBlock == 0 && responsePacket instanceof TFTPRequestPacket) {
                    // it must have missed our initial ack. Send another.
                    lastSentAck = new TFTPAckPacket(requestAddress, requestPort, acknowledgedBlock);
                    tftp.send(lastSentAck);

                    continue;
                }

                if (responsePacket instanceof TFTPDataPacket dataPacket) {
                    final byte[] data = dataPacket.getData();
                    final int dataLength = dataPacket.getDataLength();
                    final int dataOffset = dataPacket.getDataOffset();

                    final int receivedBlock = ((TFTPDataPacket) responsePacket).getBlockNumber();
                    if (receivedBlock == 0 || receivedBlock > acknowledgedBlock) {
                        outputStream.write(data, dataOffset, dataLength);
                        acknowledgedBlock = receivedBlock;
                    }

                    lastSentAck = new TFTPAckPacket(requestAddress, requestPort, receivedBlock);
                    tftp.send(lastSentAck);
                    if (dataLength < TFTPDataPacket.MAX_DATA_LENGTH) {
                        // end of stream signal - The transfer is complete.
                        outputStream.close();

                        Files.createDirectories(targetPath.getParent());
                        Files.move(temporaryPath, targetPath, StandardCopyOption.REPLACE_EXISTING);

                        if (!server) {
                            return;
                        }

                        // But my ack may be lost - so listen to see if I need to resend the ack.
                        int retries = maxRetries;
                        do {
                            try {
                                final TFTPPacket unexpectedResponsePacket = tftp.receive();
                                final InetAddress responseAddress = unexpectedResponsePacket.getAddress();
                                final int responsePort = unexpectedResponsePacket.getPort();
                                if (!responseAddress.equals(requestAddress)
                                    || !(responsePort == requestPort)) {
                                    final TFTPPacketException packetException = new TFTPPacketException(
                                        TFTPErrorPacket.UNKNOWN_TID, "Unexpected Host or Port"
                                    );
                                    LOGGER.debug("Ignoring message from unexpected source.", packetException);

                                    tftp.send(packetException.asError(responseAddress, responsePort));

                                    continue;
                                }

                                tftp.send(lastSentAck);
                            } catch (SocketTimeoutException e) {
                                // NOP
                            }
                        } while (retries-- < 0 && !Thread.interrupted());

                        return;
                    }

                    continue;
                }

                throw new TFTPPacketException(
                    TFTPErrorPacket.UNDEFINED,
                    "Unexpected response from tftp client during transfer (" + responsePacket + "). Transfer aborted."
                );
            }
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    private static OutputStream createOutputStream(Path targetPath, int mode) throws IOException {
        final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(targetPath));
        if (mode == TFTP.NETASCII_MODE) {
            return new FromNetASCIIOutputStream(outputStream);
        }

        return outputStream;
    }
}
