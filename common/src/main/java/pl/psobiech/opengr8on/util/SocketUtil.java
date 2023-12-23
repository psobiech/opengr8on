/*
 * OpenGr8on, open source extensions to systems based on Grenton devices
 * Copyright (C) 2023 Piotr Sobiech
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pl.psobiech.opengr8on.util;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.StandardSocketOptions;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import pl.psobiech.opengr8on.exceptions.UnexpectedException;

public class SocketUtil {
    public static final int DEFAULT_TIMEOUT = 9_000;

    private static final int IPTOS_RELIABILITY = 0x04;

    private SocketUtil() {
        // NOP
    }

    public static UDPSocket udp(Inet4Address address, int port) {
        return new UDPSocket(
            address, port, false, null
        );
    }

    public static UDPSocket udp(NetworkInterface networkInterface, Inet4Address address) {
        return new UDPSocket(
            address, 0, true, networkInterface
        );
    }

    public static class UDPSocket {
        private final Inet4Address address;

        private final int port;

        private final boolean broadcast;

        private final NetworkInterface networkInterface;

        private DatagramSocket socket;

        public UDPSocket(Inet4Address address, int port, boolean broadcast, NetworkInterface networkInterface) {
            this.address = address;
            this.port = port;
            this.broadcast = broadcast;
            this.networkInterface = networkInterface;
        }

        public void open() {
            synchronized (this) {
                try {
                    this.socket = new DatagramSocket(new InetSocketAddress(address, port));
                    this.socket.setSoTimeout(DEFAULT_TIMEOUT);
                    this.socket.setTrafficClass(IPTOS_RELIABILITY);

                    this.socket.setBroadcast(broadcast);
                    if (broadcast) {
                        this.socket.setOption(StandardSocketOptions.IP_MULTICAST_IF, networkInterface);
                    }
                } catch (IOException e) {
                    throw new UnexpectedException(e);
                }
            }
        }

        public void send(DatagramPacket packet) {
            synchronized (this) {
                try {
                    socket.send(packet);
                } catch (IOException e) {
                    throw new UnexpectedException(e);
                }
            }
        }

        public void discard(DatagramPacket packet) {
            synchronized (this) {
                try {
                    socket.setSoTimeout(1);
                    do {
                        socket.receive(packet);
                    } while (packet.getLength() > 0 && !Thread.interrupted());
                } catch (IOException e) {
                    // NOP
                } finally {
                    try {
                        socket.setSoTimeout(DEFAULT_TIMEOUT);
                    } catch (SocketException e) {
                        // NOP
                    }
                }
            }
        }

        public Optional<Payload> tryReceive(DatagramPacket packet, Duration timeout) {
            synchronized (this) {
                try {
                    socket.setSoTimeout(Math.toIntExact(timeout.toMillis()));
                    socket.receive(packet);
                    socket.setSoTimeout(DEFAULT_TIMEOUT);
                } catch (SocketTimeoutException e) {
                    return Optional.empty();
                } catch (IOException e) {
                    throw new UnexpectedException(e);
                }

                return Optional.of(
                    Payload.of(
                        (Inet4Address) packet.getAddress(), packet.getPort(),
                        Arrays.copyOfRange(
                            packet.getData(),
                            packet.getOffset(), packet.getOffset() + packet.getLength()
                        )
                    )
                );
            }
        }

        public void close() {
            synchronized (this) {
                FileUtil.closeQuietly(this.socket);

                this.socket = null;
            }
        }
    }

    public record Payload(Inet4Address address, int port, byte[] buffer) {
        public static Payload of(Inet4Address ipAddress, int port, byte[] buffer) {
            return new Payload(ipAddress, port, buffer);
        }

        @Override
        public String toString() {
            return "Payload{" +
                "address=" + ToStringUtil.toString(address, port) +
                ", buffer=" + ToStringUtil.toString(buffer) +
                '}';
        }
    }
}
