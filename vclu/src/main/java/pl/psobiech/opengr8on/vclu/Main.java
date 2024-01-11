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

import java.io.IOException;
import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.CLUFiles;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.Command;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.client.device.CLUDeviceConfig;
import pl.psobiech.opengr8on.client.device.CipherTypeEnum;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.HexUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil.NetworkInterfaceDto;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.util.RandomUtil;

/**
 * VCLU Entry Point
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private static final int HARDWARE_TYPE = 0x13;

    private static final int HARDWARE_VERSION = 0x01;

    private static final int FIRMWARE_TYPE = 0x03;

    private static final int FIRMWARE_VERSION = 0x00aa55aa;

    private static final int CR = 0x0D;

    private static final int LF = 0x0A;

    private static final String CRLF = Character.toString(CR) + Character.toString(LF);

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

        final CluKeys cluKeys = readOrGenerateCLUKeys(runtimeDirectory);

        LOGGER.info("Current VCLU PIN: {}", new String(cluKeys.pin(), StandardCharsets.US_ASCII));

        final String networkInterfaceNameOrIpAddress = args[args.length - 1];
        final NetworkInterfaceDto networkInterface = IPv4AddressUtil.getLocalIPv4NetworkInterfaceByName(networkInterfaceNameOrIpAddress)
                                                                    .or(() ->
                                                                        IPv4AddressUtil.getLocalIPv4NetworkInterfaceByIpAddress(networkInterfaceNameOrIpAddress)
                                                                    )
                                                                    .get();

        final CLUDevice cluDevice = readCluDevice(aDriveDirectory, networkInterface, cluKeys);

        try (Server server = new Server(rootDirectory, new CipherKey(cluKeys.key(), cluKeys.iv()), cluDevice)) {
            server.listen();
        }
    }

    private static CluKeys readOrGenerateCLUKeys(Path runtimeDirectory) throws IOException {
        final Path keysPath = runtimeDirectory.resolve("keys.json");
        if (Files.exists(keysPath)) {
            return ObjectMapperFactory.JSON.readerFor(CluKeys.class)
                                           .readValue(keysPath.toFile());
        }

        final String pin = StringUtils.upperCase(RandomUtil.hexString(Command.MAX_SERIAL_NUMBER_SIZE));

        final CluKeys cluKeys = new CluKeys(
            RandomUtil.bytes(Command.KEY_SIZE), RandomUtil.bytes(Command.IV_SIZE),
            RandomUtil.bytes(Command.IV_SIZE), pin.getBytes(StandardCharsets.US_ASCII)
        );

        ObjectMapperFactory.JSON.writerFor(CluKeys.class)
                                .writeValue(keysPath.toFile(), cluKeys);

        return cluKeys;
    }

    private static CLUDevice readCluDevice(Path aDriveDirectory, NetworkInterfaceDto networkInterface, CluKeys cluKeys) throws IOException {
        final Path configJsonPath = aDriveDirectory.resolve(CLUFiles.CONFIG_JSON.getFileName());

        final Inet4Address localAddress = networkInterface.getAddress();
        final String macAddress = macAddressAsString(networkInterface.getNetworkInterface().getHardwareAddress());

        final CLUDevice cluDevice;
        if (Files.exists(configJsonPath)) {
            final CLUDeviceConfig configJson = ObjectMapperFactory.JSON.readerFor(CLUDeviceConfig.class)
                                                                       .readValue(configJsonPath.toFile());

            cluDevice = new CLUDevice(
                configJson.getSerialNumber(), macAddress,
                localAddress,
                CipherTypeEnum.PROJECT, cluKeys.defaultIV(), cluKeys.pin()
            );

        } else {
            cluDevice = new CLUDevice(
                HexUtil.asLong(RandomUtil.hexString(Command.MAX_SERIAL_NUMBER_SIZE)), macAddress,
                localAddress,
                CipherTypeEnum.PROJECT, cluKeys.defaultIV(), cluKeys.pin()
            );
        }

        writeCluDeviceConfiguration(
            configJsonPath,
            new CLUDeviceConfig(
                HexUtil.asString(cluDevice.getSerialNumber()),
                macAddress,
                HARDWARE_TYPE, HARDWARE_VERSION,
                FIRMWARE_TYPE, FIRMWARE_VERSION,
                "OK",
                List.of()
            )
        );

        return cluDevice;
    }

    private static void writeCluDeviceConfiguration(Path configJsonPath, CLUDeviceConfig cluDevice) throws IOException {
        ObjectMapperFactory.JSON.writerFor(CLUDeviceConfig.class)
                                .writeValue(
                                    configJsonPath.toFile(),
                                    cluDevice
                                );

        final String macAddress = macAddressAsString(
            HexUtil.asBytes(
                cluDevice.getMacAddress()
                         .replaceAll(":", "")
            )
        );

        final Path configTxtPath = configJsonPath.getParent().resolve("config.txt");

        Files.writeString(
            configTxtPath,
            asConfigTxtHexValue(0x00000000) + CRLF
            + asConfigTxtHexValue(cluDevice.getSerialNumber()) + CRLF
            + macAddress + CRLF
            + asConfigTxtHexValue(cluDevice.getFirmwareType()) + CRLF
            + asConfigTxtHexValue(cluDevice.getFirmwareVersion()) + CRLF
            + asConfigTxtHexValue(cluDevice.getHardwareType()) + CRLF
            + asConfigTxtHexValue(cluDevice.getHardwareVersion()) + CRLF
        );
    }

    private static String macAddressAsString(byte[] macAddressBytes) {
        final StringBuilder macStringBuilder = new StringBuilder();
        for (byte macAddressByte : macAddressBytes) {
            if (!macStringBuilder.isEmpty()) {
                macStringBuilder.append(":");
            }

            macStringBuilder.append(HexUtil.asString(macAddressByte));
        }

        return StringUtils.lowerCase(macStringBuilder.toString());
    }

    private static String asConfigTxtHexValue(long value) {
        return StringUtils.lowerCase(
            StringUtils.leftPad(
                HexUtil.asString(value), 8, '0'
            )
        );
    }

    public record CluKeys(byte[] key, byte[] iv, byte[] defaultIV, byte[] pin) {
    }
}
