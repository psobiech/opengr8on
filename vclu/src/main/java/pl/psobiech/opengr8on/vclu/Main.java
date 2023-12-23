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

package pl.psobiech.opengr8on.vclu;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.codec.binary.Base64;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.client.device.CipherTypeEnum;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil.NetworkInterfaceDto;
import pl.psobiech.opengr8on.util.RandomUtil;

public class Main {
    public static void main(String[] args) throws Exception {
        final Path rootDirectory = Paths.get("./runtime/root/a").toAbsolutePath();
        FileUtil.mkdir(rootDirectory);

        for (NetworkInterfaceDto localIPv4NetworkInterface : IPv4AddressUtil.getLocalIPv4NetworkInterfaces()) {
            System.out.println(localIPv4NetworkInterface);
        }

        final NetworkInterfaceDto networkInterface = IPv4AddressUtil.getLocalIPv4NetworkInterfaceByName(args[args.length - 1]).get();

        final CipherKey projectCipherKey = new CipherKey(
            Base64.decodeBase64("mVHTJ/sJd9qTzE1nfLrKxA=="),
            Base64.decodeBase64("gOYp2Y1wrPT63icsX90aCA==")
        );

        // TODO: load from config
        final CLUDevice cluDevice = new CLUDevice(
            0L, "0eaa55aa55aa",
            networkInterface.getAddress(),
            CipherTypeEnum.PROJECT, RandomUtil.bytes(16), "00000000".getBytes()
        );

        try (Server server = new Server(networkInterface, rootDirectory, projectCipherKey, cluDevice)) {
            server.listen();

            while (!Thread.interrupted()) {
                Thread.yield();
            }
        }
    }
}