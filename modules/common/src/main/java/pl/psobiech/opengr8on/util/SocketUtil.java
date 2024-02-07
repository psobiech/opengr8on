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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import pl.psobiech.opengr8on.exceptions.UncheckedInterruptedException;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;

/**
 * Common socket operations.
 */
public class SocketUtil {
    /**
     * Default timeout value
     */
    public static final int DEFAULT_TIMEOUT_MILLISECONDS = 9_000;

    private static final ExecutorService PLATFORM_EXECUTOR;

    static {
        PLATFORM_EXECUTOR = ThreadUtil.daemonExecutor(SocketUtil.class);

        ThreadUtil.addShutdownHook(() -> IOUtil.closeQuietly(PLATFORM_EXECUTOR));
    }

    private SocketUtil() {
        // NOP
    }

    public static TCPClientSocket tcpClient(InetAddress address, int port) {
        return new TCPClientSocket(
            address, port
        );
    }

    public static UDPSocket udpListener(InetAddress address, int port) {
        return new UDPSocket(
            address, port, false
        );
    }

    public static UDPSocket udpRandomPort(InetAddress address) {
        return new UDPSocket(
            address, true
        );
    }

    /**
     * TCP socket wrapper
     */
    public static class TCPClientSocket implements Closeable {
        /**
         * Local network address
         */
        private final InetAddress address;

        /**
         * Local port
         */
        private final int port;

        /**
         * Socket access lock
         */
        private final ReentrantLock socketLock = new ReentrantLock();

        /**
         * Raw network socket
         */
        private Socket socket;

        /**
         * @param address local address to bind on
         * @param port port to listen on
         */
        public TCPClientSocket(InetAddress address, int port) {
            this.address = address;
            this.port    = port;
        }

        public void send(byte[] buffer) throws IOException {
            ensureConnected();

            try {
                final OutputStream outputStream = socket.getOutputStream();
                outputStream.write(buffer);
                outputStream.flush();
            } catch (SocketException e) {
                // TODO: proper retry / broken pipe handling
                disconnect();
                ensureConnected();

                try {
                    final OutputStream outputStream = socket.getOutputStream();
                    outputStream.write(buffer);
                    outputStream.flush();
                } catch (Exception e2) {
                    throw e;
                }
            }
        }

        /**
         * Disconnects the socket
         */
        public void disconnect() {
            socketLock.lock();
            try {
                // normal connect does not work for broken connections, we need to recreate socket
                close();
                open();
            } finally {
                socketLock.unlock();
            }
        }

        /**
         * Open the socket
         */
        public void open() {
            socketLock.lock();
            try {
                this.socket = new Socket();
                this.socket.setTcpNoDelay(true);
                this.socket.setKeepAlive(true);
                this.socket.setSoTimeout(DEFAULT_TIMEOUT_MILLISECONDS);
            } catch (SocketException e) {
                if (UncheckedInterruptedException.wasSocketInterrupted(e)) {
                    throw new UncheckedInterruptedException(e);
                }

                throw new UnexpectedException(e);
            } finally {
                socketLock.unlock();
            }
        }

        /**
         * @return current local address
         */
        public InetAddress getLocalAddress() {
            socketLock.lock();
            try {
                return socket.getLocalAddress();
            } finally {
                socketLock.unlock();
            }
        }

        public InputStream getInputStream() throws IOException {
            socketLock.lock();
            try {
                ensureConnected();

                return socket.getInputStream();
            } finally {
                socketLock.unlock();
            }
        }

        private void ensureConnected() throws IOException {
            socketLock.lock();
            try {
                if (!socket.isConnected()) {
                    connect();
                }
            } finally {
                socketLock.unlock();
            }
        }

        public void connect() throws IOException {
            socketLock.lock();
            try {
                this.socket.connect(new InetSocketAddress(address, port), DEFAULT_TIMEOUT_MILLISECONDS);
            } finally {
                socketLock.unlock();
            }
        }

        @Override
        public void close() {
            socketLock.lock();
            try {
                IOUtil.closeQuietly(this.socket);
            } finally {
                socketLock.unlock();
            }
        }
    }

    /**
     * UDP socket wrapper
     */
    public static class UDPSocket implements Closeable {
        /**
         * Local network address
         */
        private final InetAddress address;

        /**
         * Local port
         */
        private final int port;

        /**
         * Is broadcast enabled
         */
        private final boolean broadcast;

        /**
         * Socket access lock
         */
        private final ReentrantLock socketLock = new ReentrantLock();

        /**
         * Raw network socket
         */
        private DatagramSocket socket;

        /**
         * @param address local address to bind on (on random local port)
         * @param broadcast should broadcasting be enabled on this socket
         */
        public UDPSocket(InetAddress address, boolean broadcast) {
            this(address, 0, broadcast);
        }

        /**
         * @param address local address to bind on
         * @param port port to listen on
         * @param broadcast should broadcasting be enabled on this socket
         */
        public UDPSocket(InetAddress address, int port, boolean broadcast) {
            this.address = address;
            this.port = port;
            this.broadcast = broadcast;
        }

        /**
         * Open the socket for listening
         */
        public void open() {
            socketLock.lock();
            try {
                this.socket = new DatagramSocket(new InetSocketAddress(address, port));
                this.socket.setBroadcast(broadcast);
                this.socket.setSoTimeout(DEFAULT_TIMEOUT_MILLISECONDS);
            } catch (SocketException e) {
                if (UncheckedInterruptedException.wasSocketInterrupted(e)) {
                    throw new UncheckedInterruptedException(e);
                }

                throw new UnexpectedException(e);
            } finally {
                socketLock.unlock();
            }
        }

        /**
         * @return current local address
         */
        public InetAddress getLocalAddress() {
            return socket.getLocalAddress();
        }

        /**
         * @return current local port
         */
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

        /**
         * Discards all pending data on the socket (transmission reset)
         */
        public void discard(DatagramPacket packet) {
            socketLock.lock();
            try {
                socket.setSoTimeout(1);

                do {
                    receive(packet);
                } while (packet.getLength() > 0 && !Thread.interrupted());
            } catch (IOException e) {
                // NOP
            } finally {
                tryRestoreDefaultSoTimeout();

                socketLock.unlock();
            }
        }

        /**
         * @return payload received or empty if timeout was reached
         */
        public Optional<Payload> tryReceive(DatagramPacket packet, Duration timeout) {
            socketLock.lock();
            try {
                try {
                    // defend against 0 to prevent infinite timeouts
                    socket.setSoTimeout(Math.max(1, Math.toIntExact(timeout.toMillis())));

                    receive(packet);
                } catch (SocketTimeoutException e) {
                    return Optional.empty();
                } catch (SocketException e) {
                    if (UncheckedInterruptedException.wasSocketInterrupted(e)) {
                        throw new UncheckedInterruptedException(e);
                    }

                    throw new UnexpectedException(e);
                } catch (IOException e) {
                    throw new UnexpectedException(e);
                } finally {
                    tryRestoreDefaultSoTimeout();
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

        private void receive(DatagramPacket packet) throws IOException {
            if (
                ThreadUtil.SUPPORTS_NON_PINNING_DATAGRAM_SOCKETS
                || !Thread.currentThread().isVirtual()
            ) {
                socket.receive(packet);

                return;
            }

            final Future<?> future = PLATFORM_EXECUTOR.submit(() -> {
                socket.receive(packet);

                return null;
            });

            try {
                future.get();
            } catch (InterruptedException e) {
                ThreadUtil.cancel(future);

                throw new UncheckedInterruptedException(e);
            } catch (ExecutionException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }

                if (cause instanceof IOException ioException) {
                    throw ioException;
                }

                throw new UnexpectedException(cause);
            }
        }

        private void tryRestoreDefaultSoTimeout() {
            try {
                socket.setSoTimeout(DEFAULT_TIMEOUT_MILLISECONDS);
            } catch (SocketException e) {
                // NOP
            }
        }

        @Override
        public void close() {
            socketLock.lock();
            try {
                IOUtil.closeQuietly(this.socket);
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
                   "buffer=" + ToStringUtil.toString(buffer) +
                   ", address=" + ToStringUtil.toString(address, port) +
                   '}';
        }
    }
}
