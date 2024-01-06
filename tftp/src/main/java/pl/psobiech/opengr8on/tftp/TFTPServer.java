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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UncheckedInterruptedException;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.tftp.packets.TFTPErrorPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPErrorType;
import pl.psobiech.opengr8on.tftp.packets.TFTPPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPRequestPacket;
import pl.psobiech.opengr8on.util.SocketUtil;
import pl.psobiech.opengr8on.util.SocketUtil.UDPSocket;

public class TFTPServer implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TFTPServer.class);

    private static final Pattern PATH_PATTERN = Pattern.compile("^((?<drive>[a-zA-Z]):)?/?(?<path>.*)$");
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    private final InetAddress localAddress;

    private final ServerMode mode;

    private final Path serverDirectory;

    private final TFTP serverTFTP;

    private Future<Void> listener = null;

    public TFTPServer(NetworkInterface networkInterface, Path serverDirectory, int port, ServerMode mode) {
        this(getAddress(networkInterface), port, mode, serverDirectory);
    }

    public TFTPServer(InetAddress localAddress, int port, ServerMode mode, Path serverDirectory) {
        this(localAddress, mode, serverDirectory, SocketUtil.udpListener(localAddress, port));
    }

    public TFTPServer(InetAddress localAddress, ServerMode mode, Path serverDirectory, UDPSocket socket) {
        this.localAddress = localAddress;

        this.mode = mode;

        this.serverDirectory = serverDirectory.toAbsolutePath().normalize();

        this.serverTFTP = new TFTP(socket);
    }

    private static InetAddress getAddress(NetworkInterface networkInterface) {
        final Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
        while (addresses.hasMoreElements()) {
            final InetAddress inetAddress = addresses.nextElement();
            if (inetAddress instanceof Inet4Address) {
                return inetAddress;
            }
        }

        throw new IllegalArgumentException("No address found for interface: " + networkInterface.getName());
    }

    public Future<Void> start() throws SocketException {
        if (listener != null && !listener.isDone()) {
            return listener;
        }

        serverTFTP.open();

        listener = executorService.submit(() -> {
                LOGGER.debug("Starting TFTP Server on port " + serverTFTP.getPort() + ". Server directory: " + serverDirectory + ". Server Mode is " + mode);

                Files.createDirectories(serverDirectory);

                listen();

                return null;
            }
        );

        return listener;
    }

    public void awaitInitialized() throws InterruptedException {
        synchronized (serverTFTP) {
            serverTFTP.wait();
        }
    }

    private void listen() {
        try (serverTFTP) {
            synchronized (serverTFTP) {
                serverTFTP.notifyAll();
            }

            final int port = serverTFTP.getPort();

            do {
                TFTPPacket incomingPacket = null;
                try {
                    final Optional<TFTPPacket> incomingPacketOptional = serverTFTP.receive(DEFAULT_TIMEOUT);
                    if (incomingPacketOptional.isEmpty()) {
                        continue;
                    }

                    incomingPacket = incomingPacketOptional.get();
                    if (!(incomingPacket instanceof TFTPRequestPacket requestPacket)) {
                        throw new TFTPPacketException("Unexpected TFTP packet: " + incomingPacket.getType());
                    }

                    onRequest(requestPacket);
                } catch (TFTPPacketException e) {
                    onPacketException(e, incomingPacket);
                } catch (UncheckedInterruptedException e) {
                    LOGGER.trace(e.getMessage(), e);

                    break;
                } catch (Exception e) {
                    LOGGER.error(e.getMessage(), e);
                }
            } while (!Thread.interrupted());

            LOGGER.debug("Stopped TFTP Server on port " + port);
        }
    }

    private void onPacketException(TFTPPacketException e, TFTPPacket incomingPacket) {
        LOGGER.error(e.getMessage(), e);

        if (incomingPacket != null) {
            try {
                final TFTPErrorPacket errorPacket = e.asError(incomingPacket.getAddress(), incomingPacket.getPort());

                serverTFTP.send(errorPacket);
            } catch (Exception e2) {
                LOGGER.error(e2.getMessage(), e2);
            }
        }
    }

    private void onRequest(TFTPRequestPacket requestPacket) throws TFTPPacketException {
        final Path path = parseLocation(serverDirectory, requestPacket.getFileName());
        final TFTPTransferType transferType = TFTPTransferType.ofServerPacket(requestPacket);

        validateTransferRequest(requestPacket, transferType, path);

        LOGGER.debug("TFTP transfer " + requestPacket.getType() + " of " + requestPacket.getFileName() + " from/to " + path);

        executorService.submit(() -> {
            try (TFTP tftp = new TFTP(SocketUtil.udpRandomPort(localAddress))) {
                tftp.open();

                transferType.create(requestPacket, path)
                            .execute(tftp);
            } catch (UncheckedInterruptedException e) {
                LOGGER.trace(e.getMessage(), e);
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    private void validateTransferRequest(TFTPRequestPacket requestPacket, TFTPTransferType transferType, Path path) throws TFTPPacketException {
        switch (transferType) {
            case SERVER_READ_REQUEST -> {
                if (mode == ServerMode.PUT_ONLY) {
                    throw new TFTPPacketException(
                        TFTPErrorType.ACCESS_VIOLATION, "Read not allowed by server."
                    );
                }
            }

            case SERVER_WRITE_REQUEST -> {
                if (mode == ServerMode.GET_ONLY) {
                    throw new TFTPPacketException(
                        TFTPErrorType.ACCESS_VIOLATION, "Write not allowed by server."
                    );
                }

                if (mode != ServerMode.GET_AND_REPLACE && Files.exists(path)) {
                    throw new TFTPPacketException(
                        TFTPErrorType.FILE_EXISTS, "File already exists"
                    );
                }
            }

            default -> throw new TFTPPacketException("Unsupported TFTP packet: " + requestPacket.getType());
        }
    }

    /**
     * @param rootDirectory root directory of the server (all files need to be contained within this directory)
     * @param location TFTP location, e.g. a:\file.txt /file.txt or file.txt (in case of a:\file.txt, it will be converted to /a/file.txt)
     */
    private static Path parseLocation(Path rootDirectory, String location) throws TFTPPacketException {
        final Matcher matcher = PATH_PATTERN.matcher(location.replaceAll("\\\\", "/"));
        if (!matcher.matches()) {
            throw new TFTPPacketException(TFTPErrorType.ILLEGAL_OPERATION, "Unsupported file location: " + location);
        }

        final String drive = matcher.group("drive");
        Path parentDirectory = rootDirectory.toAbsolutePath().normalize();
        if (drive != null) {
            parentDirectory = parentDirectory.resolve(drive);
        }

        final Path path = Paths.get(matcher.group("path"));
        final Path filePath = parentDirectory.resolve(path);
        if (!filePath.startsWith(rootDirectory)) {
            throw new TFTPPacketException(TFTPErrorType.ACCESS_VIOLATION, "Cannot access files outside of TFTP server root");
        }

        return filePath;
    }

    @Override
    public void close() {
        stop();

        executorService.shutdownNow();
    }

    public Future<Void> stop() {
        final Future<Void> currentListener = listener;
        listener = null;

        if (currentListener != null) {
            currentListener.cancel(true);
        }

        return currentListener;
    }

    public enum ServerMode {
        GET_ONLY,
        PUT_ONLY,
        GET_AND_PUT,
        GET_AND_REPLACE
        //
        ;
    }
}
