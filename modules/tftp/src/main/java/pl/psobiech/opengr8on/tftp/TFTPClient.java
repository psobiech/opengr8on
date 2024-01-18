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
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.tftp.transfer.TFTPTransfer;
import pl.psobiech.opengr8on.tftp.transfer.client.TFTPClientReceive;
import pl.psobiech.opengr8on.tftp.transfer.client.TFTPClientSend;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.SocketUtil.UDPSocket;
import pl.psobiech.opengr8on.util.ThreadUtil;

public class TFTPClient implements Closeable {
    private final ExecutorService executor = ThreadUtil.daemonExecutor("TFTPClient");

    private final ReentrantLock tftpLock = new ReentrantLock();

    private final TFTP tftp;

    private final int port;

    public TFTPClient(UDPSocket socket) {
        this(socket, TFTP.DEFAULT_PORT);
    }

    public TFTPClient(UDPSocket socket, int port) {
        this.tftp = new TFTP(socket);
        this.port = port;

        tftp.open();
    }

    public void download(InetAddress host, TFTPTransferMode mode, String fileName, Path path) throws TFTPPacketException, IOException {
        execute(new TFTPClientReceive(host, port, mode, fileName, path));
    }

    public void upload(InetAddress host, TFTPTransferMode mode, Path path, String fileName) throws TFTPPacketException, IOException {
        execute(new TFTPClientSend(host, port, mode, fileName, path));
    }

    private void execute(TFTPTransfer transfer) throws IOException, TFTPPacketException {
        tftpLock.lock();
        try {
            tftp.discard();

            transfer.execute(tftp);
        } finally {
            tftpLock.unlock();
        }
    }

    @Override
    public void close() {
        ThreadUtil.closeQuietly(executor);

        IOUtil.closeQuietly(tftp);
    }
}
