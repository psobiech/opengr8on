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

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.tftp.packets.TFTPPacket;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.SocketUtil.Payload;
import pl.psobiech.opengr8on.util.SocketUtil.UDPSocket;
import pl.psobiech.opengr8on.util.ToStringUtil;

public class TFTP implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TFTP.class);

    public static final int DEFAULT_PORT = 69;

    static final int MIN_PACKET_SIZE = 4;

    static final int MAX_PACKET_SIZE = TFTPPacket.SEGMENT_SIZE + 4;

    private final byte[] receiveBuffer = new byte[MAX_PACKET_SIZE];

    protected final byte[] sendBuffer = new byte[MAX_PACKET_SIZE];

    private final UDPSocket socket;

    public TFTP(UDPSocket socket) {
        this.socket = socket;
    }

    public void open() {
        socket.open();
    }

    public int getPort() {
        return socket.getLocalPort();
    }

    public void discard() {
        socket.discard(new DatagramPacket(receiveBuffer, receiveBuffer.length));
    }

    public void send(TFTPPacket packet) throws IOException {
        LOGGER.trace("{}: {}", ">", packet);

        socket.send(packet.newDatagram(sendBuffer));
    }

    public Optional<TFTPPacket> receive(Duration timeout) throws TFTPPacketException {
        final DatagramPacket datagramPacket = new DatagramPacket(receiveBuffer, 0, receiveBuffer.length);

        do {
            final long startedAt = System.nanoTime();

            final Optional<Payload> payloadOptional = socket.tryReceive(datagramPacket, timeout);
            if (payloadOptional.isPresent()) {
                final Payload payload = payloadOptional.get();

                final byte[] buffer = payload.buffer();
                if (buffer.length < MIN_PACKET_SIZE) {
                    throw new TFTPPacketException("Bad packet. Datagram data length is too short: " + ToStringUtil.toString(buffer));
                }

                try {
                    final TFTPPacket newTFTPPacket = TFTPPacket.newTFTPPacket(payload);
                    LOGGER.trace("{}: {}", "<", newTFTPPacket);

                    return Optional.of(newTFTPPacket);
                } catch (TFTPPacketException e) {
                    LOGGER.warn(e.getMessage(), e);
                }
            }

            timeout = timeout.minusNanos(System.nanoTime() - startedAt);
        } while (timeout.isPositive() && !Thread.interrupted());

        return Optional.empty();
    }

    @Override
    public void close() {
        IOUtil.closeQuietly(socket);
    }
}
