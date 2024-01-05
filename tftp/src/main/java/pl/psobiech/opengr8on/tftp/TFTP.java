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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.SocketTimeoutException;
import java.time.Duration;

import org.apache.commons.net.DatagramSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.tftp.packets.TFTPPacket;

public class TFTP extends DatagramSocketClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(TFTP.class);

    private static final int DEFAULT_TIMEOUT = 5000;

    public static final Duration DEFAULT_TIMEOUT_DURATION = Duration.ofMillis(DEFAULT_TIMEOUT);

    public static final int DEFAULT_PORT = 69;

    static final int MAX_PACKET_SIZE = TFTPPacket.SEGMENT_SIZE + 4;

    private final byte[] receiveBuffer = new byte[MAX_PACKET_SIZE];

    protected final byte[] sendBuffer = new byte[MAX_PACKET_SIZE];

    public TFTP() {
        setDefaultTimeout(DEFAULT_TIMEOUT_DURATION);
    }

    public void send(TFTPPacket packet) throws IOException {
        LOGGER.trace("{}: {}", ">", packet);

        checkOpen().send(
            packet.newDatagram(sendBuffer)
        );
    }

    public TFTPPacket receive() throws IOException, TFTPPacketException {
        final DatagramPacket datagramPacket = new DatagramPacket(receiveBuffer, 0, receiveBuffer.length);

        do {
            try {
                checkOpen().receive(datagramPacket);

                final TFTPPacket newTFTPPacket = TFTPPacket.newTFTPPacket(datagramPacket);
                LOGGER.trace("{}: {}", "<", newTFTPPacket);

                return newTFTPPacket;
            } catch (SocketTimeoutException e) {
                // NOP
            }
        } while (!Thread.interrupted());

        throw new InterruptedIOException();
    }
}
