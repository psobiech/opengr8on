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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.tftp.packets.TFTPErrorPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPReadRequestPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPWriteRequestPacket;
import pl.psobiech.opengr8on.tftp.transfer.TFTPServerReceive;
import pl.psobiech.opengr8on.tftp.transfer.TFTPServerSend;
import pl.psobiech.opengr8on.tftp.transfer.TFTPTransfer;

public class TFTPServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TFTPServer.class);

    private static final Pattern PATH_PATTERN = Pattern.compile("^((?<drive>[a-zA-Z]):)?/?(?<path>.*)$");

    public static Future<Void> create(NetworkInterface networkInterface, Path serverDirectory, int port, ServerMode mode) {
        return create(getAddress(networkInterface), serverDirectory, port, mode);
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

    public static Future<Void> create(InetAddress localAddress, Path serverDirectory, int port, ServerMode mode) {
        final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

        final TFTP mainTFTP = new TFTP();
        try {
            if (localAddress == null) {
                mainTFTP.open(port);
            } else {
                mainTFTP.open(port, localAddress);
            }
        } catch (SocketException e) {
            throw new UnexpectedException(e);
        }

        return executorService.submit(() -> {
            LOGGER.debug("Starting TFTP Server on port " + port + ".  Server directory: " + serverDirectory + ". Server Mode is " + mode);
            Files.createDirectories(serverDirectory);

            try (mainTFTP) {
                do {
                    final TFTPPacket incomingPacket = mainTFTP.receive();

                    final TFTPTransfer transfer;
                    if (incomingPacket instanceof TFTPReadRequestPacket packet) {
                        transfer = createTransfer(mainTFTP, mode, serverDirectory, packet);
                    } else if (incomingPacket instanceof TFTPWriteRequestPacket packet) {
                        transfer = createTransfer(mainTFTP, mode, serverDirectory, packet);
                    } else {
                        LOGGER.error("Unexpected TFTP packet: " + incomingPacket.getType());

                        continue;
                    }

                    executorService.submit(() -> {
                        try (TFTP tftp = new TFTP()) {
                            tftp.open();

                            transfer.execute(tftp);
                        } catch (Exception e) {
                            LOGGER.error(e.getMessage(), e);
                        }

                        return null;
                    });
                } while (!Thread.interrupted());
            } finally {
                executorService.shutdownNow();
            }

            return null;
        });
    }

    private static TFTPTransfer createTransfer(
        TFTP mainTFTP, ServerMode mode, Path serverDirectory,
        TFTPReadRequestPacket packet
    ) throws IOException, TFTPPacketException {
        if (mode == ServerMode.PUT_ONLY) {
            final TFTPPacketException packetException = new TFTPPacketException(TFTPErrorPacket.ILLEGAL_OPERATION, "Read not allowed by server.");
            mainTFTP.send(packetException.asError(packet.getAddress(), packet.getPort()));

            throw packetException;
        }

        final Path path = getPath(serverDirectory, packet.getFileName());

        return new TFTPServerSend(packet, path);
    }

    private static TFTPTransfer createTransfer(
        TFTP mainTFTP, ServerMode mode, Path serverDirectory,
        TFTPWriteRequestPacket packet
    ) throws IOException, TFTPPacketException {
        if (mode == ServerMode.GET_ONLY) {
            final TFTPPacketException packetException = new TFTPPacketException(TFTPErrorPacket.ILLEGAL_OPERATION, "Write not allowed by server.");
            mainTFTP.send(packetException.asError(packet.getAddress(), packet.getPort()));

            throw packetException;
        }

        final Path path = getPath(serverDirectory, packet.getFileName());
        if (mode != ServerMode.GET_AND_REPLACE && Files.exists(path)) {
            final TFTPPacketException packetException = new TFTPPacketException(TFTPErrorPacket.FILE_EXISTS, "File already exists");
            mainTFTP.send(packetException.asError(packet.getAddress(), packet.getPort()));

            throw packetException;
        }

        return new TFTPServerReceive(packet, path);
    }

    /*
     * Makes sure that paths provided by TFTP clients do not get outside the serverRoot directory.
     */
    private static Path getPath(Path serverDirectory, String fileName) throws IOException {
        final Matcher matcher = PATH_PATTERN.matcher(fileName.replaceAll("\\\\", "/"));
        if (!matcher.matches()) {
            throw new IOException("Unsupported file location: " + fileName);
        }

        Path parentDirectory = serverDirectory.toAbsolutePath();

        final String drive = matcher.group("drive");
        if (drive != null) {
            parentDirectory = parentDirectory.resolve(drive);
        }

        final Path filePath = parentDirectory.resolve(Paths.get(matcher.group("path")));
        if (!filePath.startsWith(serverDirectory)) {
            throw new IOException("Cannot access files outside of TFTP server root");
        }

        return filePath;
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
