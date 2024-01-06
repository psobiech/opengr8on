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

package pl.psobiech.opengr8on.util;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import pl.psobiech.opengr8on.exceptions.UncheckedInterruptedException;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;

public class SocketUtil {
    public static final int DEFAULT_TIMEOUT = 9_000;

    private static final int IPTOS_RELIABILITY = 0x04;

    private SocketUtil() {
        // NOP
    }

    public static UDPSocket udpListener(InetAddress address, int port) {
        return new UDPSocket(
            address, port, false
        );
    }

    public static UDPSocket udpRandomPort(InetAddress address) {
        return new UDPSocket(
            address, 0, true
        );
    }

    public static class UDPSocket implements Closeable {
        private final InetAddress address;

        private final int port;

        private final boolean broadcast;

        private final ReentrantLock socketLock = new ReentrantLock();

        private DatagramSocket socket;

        public UDPSocket(InetAddress address, int port, boolean broadcast) {
            this.address = address;
            this.port = port;
            this.broadcast = broadcast;
        }

        public void open() {
            socketLock.lock();
            try {
                this.socket = new DatagramSocket(new InetSocketAddress(address, port));
                this.socket.setSoTimeout(DEFAULT_TIMEOUT);
                this.socket.setTrafficClass(IPTOS_RELIABILITY);

                this.socket.setBroadcast(broadcast);
            } catch (IOException e) {
                throw new UnexpectedException(e);
            } finally {
                socketLock.unlock();
            }
        }

        public InetAddress getLocalAddress() {
            return socket.getLocalAddress();
        }

        public int getLocalPort() {
            return socket.getLocalPort();
        }

        public void send(DatagramPacket packet) {
            socketLock.lock();
            try {
                socket.send(packet);
            } catch (IOException e) {
                throw new UnexpectedException(e);
            } finally {
                socketLock.unlock();
            }
        }

        public void discard(DatagramPacket packet) {
            socketLock.lock();
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

                socketLock.unlock();
            }
        }

        public Optional<Payload> tryReceive(DatagramPacket packet, Duration timeout) {
            socketLock.lock();
            try {
                try {
                    // defend against 0 to prevent infinite timeouts
                    socket.setSoTimeout(Math.max(1, Math.toIntExact(timeout.toMillis())));
                    socket.receive(packet);
                    socket.setSoTimeout(DEFAULT_TIMEOUT);
                } catch (SocketTimeoutException e) {
                    return Optional.empty();
                } catch (SocketException e) {
                    if (UncheckedInterruptedException.wasSocketInterrupted(e)) {
                        throw new UncheckedInterruptedException(e);
                    }

                    throw new UnexpectedException(e);
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
            } finally {
                socketLock.unlock();
            }
        }

        @Override
        public void close() {
            socketLock.lock();
            try {
                FileUtil.closeQuietly(this.socket);

                this.socket = null;
            } finally {
                socketLock.unlock();
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
