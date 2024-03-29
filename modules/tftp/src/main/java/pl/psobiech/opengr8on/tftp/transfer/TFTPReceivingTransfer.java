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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.tftp.TFTP;
import pl.psobiech.opengr8on.tftp.TFTPTransferMode;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPException;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.tftp.packets.TFTPAcknowledgementPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPDataPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPErrorPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPErrorType;
import pl.psobiech.opengr8on.tftp.packets.TFTPPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPRequestPacket;
import pl.psobiech.opengr8on.tftp.transfer.netascii.FromNetASCIIOutputStream;
import pl.psobiech.opengr8on.util.FileUtil;

public abstract class TFTPReceivingTransfer extends TFTPTransfer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TFTPReceivingTransfer.class);

    protected void incomingTransfer(
        TFTP tftp, boolean server,
        TFTPTransferMode mode,
        InetAddress requestAddress, int requestPort,
        TFTPPacket lastSentPacket, Path targetPath
    ) throws IOException, TFTPPacketException {
        final Path temporaryPath = FileUtil.temporaryFile();

        final OutputStream outputStream;
        try {
            // check if file is writable
            FileUtil.touch(targetPath);

            outputStream = createOutputStream(temporaryPath, mode);
        } catch (Exception e) {
            FileUtil.deleteQuietly(temporaryPath);

            final TFTPPacketException packetException = new TFTPPacketException(TFTPErrorType.UNDEFINED, e.getMessage(), e);
            tftp.send(packetException.asError(requestAddress, requestPort));

            throw packetException;
        }

        try (outputStream) {
            if (server) {
                tftp.send(lastSentPacket);
            }

            boolean firstPacket = !server;
            int acknowledgedBlock = 0;
            do {
                final TFTPPacket responsePacket = readResponsePacket(tftp, firstPacket, requestAddress, requestPort, lastSentPacket);
                if (firstPacket) {
                    firstPacket = false;

                    requestAddress = responsePacket.getAddress();
                    requestPort    = responsePacket.getPort();
                }

                if (server && acknowledgedBlock == 0 && responsePacket instanceof TFTPRequestPacket) {
                    // it must have missed our initial ack. Send another.
                    lastSentPacket = new TFTPAcknowledgementPacket(requestAddress, requestPort, acknowledgedBlock);
                    tftp.send(lastSentPacket);

                    continue;
                }

                if (responsePacket instanceof TFTPDataPacket dataPacket) {
                    final byte[] data = dataPacket.getBuffer();
                    final int dataLength = dataPacket.getDataLength();
                    final int dataOffset = dataPacket.getDataOffset();

                    final int receivedBlock = ((TFTPDataPacket) responsePacket).getBlockNumber();
                    if (receivedBlock == 0 || receivedBlock > acknowledgedBlock) {
                        outputStream.write(data, dataOffset, dataLength);
                        acknowledgedBlock = receivedBlock;
                    }

                    if (dataLength >= TFTPDataPacket.MAX_DATA_LENGTH) {
                        lastSentPacket = new TFTPAcknowledgementPacket(requestAddress, requestPort, receivedBlock);
                        tftp.send(lastSentPacket);
                    } else {
                        try {
                            // end of stream signal - The transfer is complete.
                            outputStream.close();

                            FileUtil.mkdir(targetPath.getParent());
                            FileUtil.linkOrCopy(temporaryPath, targetPath);
                        } catch (Exception e) {
                            throw new TFTPPacketException(TFTPErrorType.UNDEFINED, e.getMessage(), e);
                        }

                        lastSentPacket = new TFTPAcknowledgementPacket(requestAddress, requestPort, receivedBlock);
                        tftp.send(lastSentPacket);

                        if (!server) {
                            return;
                        }

                        // But my ack may be lost - so listen to see if I need to resend the ack.
                        do {
                            final Optional<TFTPPacket> repeatResponsePacketOptional = tftp.receive(DEFAULT_TIMEOUT);
                            if (repeatResponsePacketOptional.isEmpty()) {
                                return;
                            }

                            final TFTPPacket unexpectedResponsePacket = repeatResponsePacketOptional.get();
                            final InetAddress responseAddress = unexpectedResponsePacket.getAddress();
                            final int responsePort = unexpectedResponsePacket.getPort();
                            if (!responseAddress.equals(requestAddress)
                                || !(responsePort == requestPort)) {
                                final TFTPPacketException packetException = new TFTPPacketException(
                                    TFTPErrorType.UNKNOWN_TID, "Unexpected Host or Port"
                                );
                                LOGGER.debug("Ignoring message from unexpected source.", packetException);

                                tftp.send(packetException.asError(responseAddress, responsePort));

                                continue;
                            }

                            tftp.send(lastSentPacket);
                        } while (!Thread.interrupted());

                        return;
                    }

                    continue;
                }

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
            } while (!Thread.interrupted());
        } finally {
            FileUtil.deleteQuietly(temporaryPath);
        }
    }

    private static OutputStream createOutputStream(Path targetPath, TFTPTransferMode mode) throws IOException {
        final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(targetPath));
        if (mode == TFTPTransferMode.NETASCII) {
            return new FromNetASCIIOutputStream(outputStream);
        }

        return outputStream;
    }
}
