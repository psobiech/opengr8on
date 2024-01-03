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
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;

import org.apache.commons.net.DatagramSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.tftp.packets.TFTPPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPRequestPacket;

public class TFTP extends DatagramSocketClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(TFTP.class);

    /**
     * The ascii transfer mode. Equivalent to NETASCII_MODE
     */
    public static final int ASCII_MODE = 0;

    /**
     * The netascii transfer mode.
     */
    public static final int NETASCII_MODE = ASCII_MODE;

    /**
     * The binary transfer mode. Equivalent to OCTET_MODE.
     */
    public static final int BINARY_MODE = 1;

    /**
     * The octet transfer mode.
     */
    public static final int OCTET_MODE = BINARY_MODE;

    private static final int DEFAULT_TIMEOUT = 5000;

    public static final Duration DEFAULT_TIMEOUT_DURATION = Duration.ofMillis(DEFAULT_TIMEOUT);

    public static final int DEFAULT_PORT = 69;

    static final int MAX_PACKET_SIZE = TFTPPacket.SEGMENT_SIZE + 4;

    private final byte[] receiveBuffer = new byte[MAX_PACKET_SIZE];

    protected final byte[] sendBuffer = new byte[MAX_PACKET_SIZE];

    public TFTP() {
        setDefaultTimeout(DEFAULT_TIMEOUT_DURATION);
    }

    public void discardPackets() throws IOException {
        final DatagramPacket datagram = newDatagramPacket();
        final Duration soTimeoutDuration = getSoTimeoutDuration();

        setSoTimeout(Duration.ofMillis(1));
        try {
            do {
                checkOpen().receive(datagram);
            } while (!Thread.interrupted());
        } catch (SocketException e) {
            // Do nothing. We timed out, so we hope we're caught up.
        } finally {
            setSoTimeout(soTimeoutDuration);
        }
    }

    public static String getModeName(int mode) {
        return TFTPRequestPacket.MODES.get(mode);
    }

    public void send(TFTPPacket packet) throws IOException {
        sendPacket(packet, packet.newDatagram(sendBuffer));
    }

    private void sendPacket(TFTPPacket packet, DatagramPacket datagramPacket) throws IOException {
        LOGGER.trace("{}: {}", ">", packet);
        checkOpen().send(datagramPacket);
    }

    public TFTPPacket receive() throws IOException, TFTPPacketException {
        final DatagramPacket datagramPacket = new DatagramPacket(receiveBuffer, 0, receiveBuffer.length);

        return receivePacket(datagramPacket);
    }

    /**
     * Receives a TFTPPacket.
     *
     * @return The TFTPPacket received.
     * @throws InterruptedIOException If a socket timeout occurs. The Java documentation claims an InterruptedIOException is thrown on a DatagramSocket timeout,
     * but in practice we find a SocketException is thrown. You should catch both to be safe.
     * @throws IOException If some other I/O error occurs.
     * @throws TFTPPacketException If an invalid TFTP packet is received.
     */
    private TFTPPacket receivePacket(DatagramPacket packet) throws IOException, InterruptedIOException, TFTPPacketException {
        do {
            try {
                checkOpen().receive(packet);

                final TFTPPacket newTFTPPacket = TFTPPacket.newTFTPPacket(packet);
                LOGGER.trace("{}: {}", "<", newTFTPPacket);

                return newTFTPPacket;
            } catch (SocketTimeoutException e) {
                // NOP
            }
        } while (!Thread.interrupted());

        throw new InterruptedIOException();
    }

    public static DatagramPacket newDatagramPacket() {
        return new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
    }
}
