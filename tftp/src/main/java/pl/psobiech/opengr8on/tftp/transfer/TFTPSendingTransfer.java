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

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UncheckedInterruptedException;
import pl.psobiech.opengr8on.tftp.TFTP;
import pl.psobiech.opengr8on.tftp.TFTPTransferMode;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPException;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.tftp.packets.TFTPAckPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPDataPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPErrorType;
import pl.psobiech.opengr8on.tftp.packets.TFTPPacket;

public abstract class TFTPSendingTransfer extends TFTPTransfer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TFTPSendingTransfer.class);

    protected void outgoingTransfer(
        TFTP tftp, boolean server,
        Path path, TFTPTransferMode mode,
        InetAddress requestAddress, int requestPort
    ) throws TFTPPacketException, IOException {
        try (InputStream inputStream = createInputStream(path, mode)) {
            int retry = maxRetries;

            boolean firstPacket = !server;

            int block = 1;
            boolean readNextBlock = true;
            TFTPDataPacket requestPacket = null;

            int lastRead = 0;
            final byte[] buffer = new byte[TFTPDataPacket.MAX_DATA_LENGTH];
            do {
                if (readNextBlock) {
                    int offset = 0;

                    int read;
                    while (offset < buffer.length && (read = inputStream.read(buffer, offset, buffer.length - offset)) >= 0) {
                        offset += read;
                    }

                    lastRead = offset;

                    requestPacket = new TFTPDataPacket(requestAddress, requestPort, block, buffer, 0, lastRead);
                    tftp.send(requestPacket);
                }

                final TFTPPacket responsePacket = readResponsePacket(tftp, firstPacket, requestAddress, requestPort, requestPacket);
                if (firstPacket) {
                    firstPacket = false;

                    requestAddress = responsePacket.getAddress();
                    requestPort    = responsePacket.getPort();
                }

                if (!(responsePacket instanceof final TFTPAckPacket ack)) {
                    throw new TFTPException(
                        TFTPErrorType.UNDEFINED, "Unexpected response from tftp client during transfer (" + responsePacket + "). Transfer aborted."
                    );
                }

                // once we get here, we know we have an answer packet from the correct host.
                if (ack.getBlockNumber() == block) {
                    // next block number
                    block = (block + 1) % (0xFFFF + 1);

                    readNextBlock = true;
                } else {
                    /*
                     * The original tftp spec would have called on us to resend the previous data here, however, that causes the SAS Syndrome.
                     * http://www.faqs.org/rfcs/rfc1123.html section 4.2.3.1 The modified spec says that we ignore a duplicate ack. If the packet was really
                     * lost, we will time out on receive, and resend the previous data at that point.
                     */
                    readNextBlock = false;

                    if (retry-- < 0) {
                        throw new TFTPException(
                            TFTPErrorType.UNDEFINED, "Communication error, no more retries available"
                        );
                    }
                }
            } while (lastRead == TFTPDataPacket.MAX_DATA_LENGTH && !Thread.interrupted());
        } catch (TFTPPacketException packetException) {
            tftp.send(packetException.asError(requestAddress, requestPort));

            throw packetException;
        } catch (FileNotFoundException | NoSuchFileException e) {
            final TFTPPacketException packetException = new TFTPPacketException(
                TFTPErrorType.FILE_NOT_FOUND, e.getMessage(), e
            );
            tftp.send(packetException.asError(requestAddress, requestPort));

            throw packetException;
        } catch (UncheckedInterruptedException e) {
            throw e;
        } catch (Exception e) {
            final TFTPPacketException packetException = new TFTPPacketException(
                TFTPErrorType.UNDEFINED, e.getMessage(), e
            );
            tftp.send(packetException.asError(requestAddress, requestPort));

            throw packetException;
        }
    }

    protected static InputStream createInputStream(Path path, TFTPTransferMode mode) throws IOException {
        final InputStream inputStream = new BufferedInputStream(Files.newInputStream(path));
        if (mode == TFTPTransferMode.NETASCII) {
            return new ToNetASCIIInputStream(inputStream);
        }

        return inputStream;
    }
}
