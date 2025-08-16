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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import pl.psobiech.opengr8on.client.CLUFiles;
import pl.psobiech.opengr8on.client.Command;
import pl.psobiech.opengr8on.client.device.CLUDeviceConfig;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.HexUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil.NetworkInterfaceDto;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.util.RandomUtil;
import pl.psobiech.opengr8on.vclu.Main.CluKeys;

public class Config {
    private static final int HARDWARE_TYPE = 0x13;

    private static final int HARDWARE_VERSION = 0x01;

    private static final int FIRMWARE_TYPE = 0x03;

    private static final int FIRMWARE_VERSION = 0x00aa55aa;

    private Config() {
        // NOP
    }

    public static CluKeys readOrGenerateKeys(Path runtimeDirectory) throws IOException {
        final Path keysPath = runtimeDirectory.resolve("keys.json");
        if (Files.exists(keysPath)) {
            return ObjectMapperFactory.JSON.readerFor(CluKeys.class)
                    .readValue(keysPath.toFile());
        }

        final String pin = StringUtils.upperCase(RandomUtil.hexString(Command.MAX_SERIAL_NUMBER_CHARACTERS));

        final byte[] ivBytes = RandomUtil.bytes(Command.IV_BYTES);
        final CluKeys cluKeys = new CluKeys(
                RandomUtil.bytes(Command.KEY_BYTES), ivBytes,
                ivBytes, pin.getBytes(StandardCharsets.US_ASCII)
        );

        writeKeys(runtimeDirectory, cluKeys);

        return cluKeys;
    }

    public static void writeKeys(Path runtimeDirectory, CluKeys cluKeys) throws IOException {
        final Path keysPath = runtimeDirectory.resolve("keys.json");

        ObjectMapperFactory.JSON.writerFor(CluKeys.class)
                .writeValue(keysPath.toFile(), cluKeys);
    }

    public static CLUDeviceConfig read(Path aDriveDirectory, NetworkInterfaceDto networkInterface) throws IOException {
        final String macAddress = macAddressAsString(
                networkInterface.getNetworkInterface()
                        .getHardwareAddress()
        );

        final CLUDeviceConfig cluDeviceConfig;
        final Path configJsonPath = aDriveDirectory.resolve(CLUFiles.CONFIG_JSON.getFileName());
        if (Files.exists(configJsonPath)) {
            final CLUDeviceConfig configJson = ObjectMapperFactory.JSON.readerFor(CLUDeviceConfig.class)
                    .readValue(configJsonPath.toFile());

            cluDeviceConfig = new CLUDeviceConfig(
                    configJson.getSerialNumber(),
                    macAddress,
                    configJson.getHardwareType(), configJson.getHardwareVersion(),
                    configJson.getFirmwareType(), configJson.getFirmwareVersion(),
                    configJson.getFirmwareVersionString(),
                    configJson.getStatus(),
                    configJson.getTFBusDevices()
            );
        } else {
            cluDeviceConfig = new CLUDeviceConfig(
                    HexUtil.asLong(RandomUtil.hexString(Command.MAX_SERIAL_NUMBER_CHARACTERS)),
                    macAddress,
                    HARDWARE_TYPE, HARDWARE_VERSION,
                    FIRMWARE_TYPE, FIRMWARE_VERSION,
                    HexUtil.asString(FIRMWARE_VERSION) + "-0000",
                    "OK",
                    List.of()
            );
        }

        write(
                aDriveDirectory,
                cluDeviceConfig
        );

        return cluDeviceConfig;
    }

    public static void write(Path aDriveDirectory, CLUDeviceConfig cluDevice) throws IOException {
        final Path configJsonPath = aDriveDirectory.resolve(CLUFiles.CONFIG_JSON.getFileName());

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
                asConfigTxtHexValue(0x00000000) + FileUtil.CRLF
                        + asConfigTxtHexValue(cluDevice.getSerialNumber()) + FileUtil.CRLF
                        + macAddress + FileUtil.CRLF
                        + asConfigTxtHexValue(cluDevice.getFirmwareType()) + FileUtil.CRLF
                        + asConfigTxtHexValue(cluDevice.getFirmwareVersion()) + FileUtil.CRLF
                        + asConfigTxtHexValue(cluDevice.getHardwareType()) + FileUtil.CRLF
                        + asConfigTxtHexValue(cluDevice.getHardwareVersion()) + FileUtil.CRLF
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
                        HexUtil.asString(value), Integer.BYTES * 2, '0'
                )
        );
    }

}
