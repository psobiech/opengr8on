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

package pl.psobiech.opengr8on.vclu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.client.device.CLUDeviceConfig;
import pl.psobiech.opengr8on.client.device.CipherTypeEnum;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil.NetworkInterfaceDto;

import java.io.IOException;
import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

/**
 * VCLU Entry Point
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private Main() {
        // NOP
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new UnexpectedException("Missing argument: Network Interface Name or local IP Address");
        }

        final Path runtimeDirectory = Paths.get("./runtime").toAbsolutePath();
        final Path rootDirectory = runtimeDirectory.resolve("root");

        final Path aDriveDirectory = rootDirectory.resolve("a");
        FileUtil.mkdir(aDriveDirectory);

        final CluKeys cluKeys = Config.readOrGenerateKeys(runtimeDirectory);

        LOGGER.info("Current VCLU PIN: {}", new String(cluKeys.pin(), StandardCharsets.US_ASCII));

        final String networkInterfaceNameOrIpAddress = args[args.length - 1];
        final NetworkInterfaceDto networkInterface = IPv4AddressUtil.getLocalIPv4NetworkInterfaceByName(networkInterfaceNameOrIpAddress)
                                                                    .or(() ->
                                                                                IPv4AddressUtil.getLocalIPv4NetworkInterfaceByIpAddress(networkInterfaceNameOrIpAddress)
                                                                    )
                                                                    .get();

        final CLUDevice cluDevice = readCluDevice(aDriveDirectory, networkInterface, cluKeys);

        try (Server server = new Server(rootDirectory, new CipherKey(cluKeys.key(), cluKeys.iv()), networkInterface, cluDevice)) {
            server.start();

            // sleep until interrupted
            new CountDownLatch(1).await();
        }
    }

    private static CLUDevice readCluDevice(Path aDriveDirectory, NetworkInterfaceDto networkInterface, CluKeys cluKeys) throws IOException {
        final Inet4Address localAddress = networkInterface.getAddress();

        final CLUDeviceConfig cluDeviceConfig = Config.read(aDriveDirectory, networkInterface);

        return new CLUDevice(
                cluDeviceConfig.getSerialNumber(), cluDeviceConfig.getMacAddress(),
                localAddress,
                CipherTypeEnum.PROJECT, cluKeys.defaultIV(), cluKeys.pin()
        );
    }

    public record CluKeys(byte[] key, byte[] iv, byte[] defaultIV, byte[] pin) {
    }
}
