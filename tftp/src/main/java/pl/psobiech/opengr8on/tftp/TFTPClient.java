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
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import pl.psobiech.opengr8on.tftp.transfer.client.TFTPClientReceive;
import pl.psobiech.opengr8on.tftp.transfer.client.TFTPClientSend;
import pl.psobiech.opengr8on.tftp.transfer.TFTPTransfer;

public class TFTPClient implements Closeable {
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    public Future<Void> receive(String fileName, InetAddress host, int port, TFTPTransferMode mode, Path path) {
        return transfer(
            new TFTPClientReceive(host, port, mode, fileName, path)
        );
    }

    public Future<Void> send(Path path, TFTPTransferMode mode, InetAddress host, int port, String fileName) {
        return transfer(
            new TFTPClientSend(host, port, mode, fileName, path)
        );
    }

    private Future<Void> transfer(TFTPTransfer transfer) {
        return executorService.submit(() -> {
            try (TFTP tftp = new TFTP()) {
                tftp.open();

                transfer.execute(tftp);
            }

            return null;
        });
    }

    @Override
    public void close() {
        executorService.shutdownNow();
    }
}
