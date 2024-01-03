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

import java.nio.file.Path;
import java.nio.file.Paths;

import pl.psobiech.opengr8on.client.CLUFiles;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.client.device.CLUDeviceConfig;
import pl.psobiech.opengr8on.client.device.CipherTypeEnum;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil.NetworkInterfaceDto;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;

public class Main {
    static {
        System.setProperty("jdk.tracePinnedThreads", "full/short");
    }

    private Main() {
        // NOP
    }

    public static void main(String[] args) throws Exception {
        final Path rootDirectory = Paths.get("./runtime/root").toAbsolutePath();

        final Path aDriveDirectory = rootDirectory.resolve("a");
        FileUtil.mkdir(aDriveDirectory);

        final CluKeys cluKeys = ObjectMapperFactory.JSON.readerFor(CluKeys.class)
                                                        .readValue(rootDirectory.resolve("../keys.json").toFile());

        final CipherKey projectCipherKey = new CipherKey(
            cluKeys.key(), cluKeys.iv()
        );

        final CLUDeviceConfig configJson = ObjectMapperFactory.JSON.readerFor(CLUDeviceConfig.class)
                                                                   .readValue(aDriveDirectory.resolve(CLUFiles.CONFIG_JSON.getFileName()).toFile());

        if (args.length < 1) {
            throw new UnexpectedException("Missing argument: Network Interface Name or IP Address");
        }

        final String networkInterfaceNameOrIpAddress = args[args.length - 1];
        final NetworkInterfaceDto networkInterface = IPv4AddressUtil.getLocalIPv4NetworkInterfaceByName(networkInterfaceNameOrIpAddress)
                                                                    .or(() ->
                                                                        IPv4AddressUtil.getLocalIPv4NetworkInterfaceByIpAddress(networkInterfaceNameOrIpAddress)
                                                                    )
                                                                    .get();
        final CLUDevice cluDevice = new CLUDevice(
            configJson.getSerialNumber(), configJson.getMacAddress(),
            networkInterface.getAddress(),
            CipherTypeEnum.PROJECT, cluKeys.defaultIV(), cluKeys.pin()
        );

        try (Server server = new Server(networkInterface, rootDirectory, projectCipherKey, cluDevice)) {
            server.listen();
        }
    }

    public record CluKeys(byte[] key, byte[] iv, byte[] defaultIV, byte[] pin) {
    }
}
