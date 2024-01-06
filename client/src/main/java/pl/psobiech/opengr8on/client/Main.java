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

package pl.psobiech.opengr8on.client;

import java.io.IOException;
import java.net.Inet4Address;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.client.device.CLUDeviceConfig;
import pl.psobiech.opengr8on.client.device.CipherTypeEnum;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil.NetworkInterfaceDto;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.util.RandomUtil;
import pl.psobiech.opengr8on.util.Util;
import pl.psobiech.opengr8on.xml.interfaces.CLU;
import pl.psobiech.opengr8on.xml.interfaces.InterfaceRegistry;
import pl.psobiech.opengr8on.xml.omp.OmpReader;

public class Main {
    private static final Duration DEFAULT_LONG_TIMEOUT = Duration.ofMillis(30_000);

    private static final int TIMEOUT = 4_000;

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private static final Map<Long, byte[]> PRIVATE_KEYS = Map.of(
        0x0L, "00000000".getBytes()
    );

    private static final Inet4Address MIN_IP = IPv4AddressUtil.parseIPv4("10.72.144.1");

    private Main() {
        // NOP
    }

    public static void main(String[] args) throws Exception {
        final CommandLine commandLine = new DefaultParser().parse(CLIParameters.OPTIONS, args);
        if (commandLine.hasOption(CLIParameters.HELP_OPTION)) {
            new HelpFormatter()
                .printHelp("java -jar client.jar", CLIParameters.OPTIONS);

            System.exit(0);
        }

        //

        final NetworkInterfaceDto networkInterface = CLIParameters.getNetworkInterface(commandLine);
        LOGGER.debug("Using network interface: {}", networkInterface);

        if (commandLine.hasOption(CLIParameters.DISCOVER_OPTION)) {
            final InterfaceRegistry interfaceRegistry = CLIParameters.getInterfaceRegistry(commandLine);
            final CipherKey projectCipherKey = CLIParameters.getProjectCipherKey(commandLine)
                                                            .orElseGet(() -> {
                                                                final CipherKey cipherKey = new CipherKey(RandomUtil.bytes(16), RandomUtil.bytes(16));
                                                                LOGGER.debug("Generated random project key: {}", cipherKey);

                                                                return cipherKey;
                                                            });

            final Integer cluLimit = Optional.ofNullable(commandLine.getOptionValue(CLIParameters.CLU_LIMIT_PATH_OPTION))
                                             .map(Integer::parseInt)
                                             .orElse(1);

            discover(networkInterface, projectCipherKey, cluLimit, interfaceRegistry);

            return;
        }

        final Inet4Address ipAddress = CLIParameters.getRemoteIPAddress(commandLine);

        if (commandLine.hasOption(CLIParameters.FETCH_OPTION)) {
            final InterfaceRegistry interfaceRegistry = CLIParameters.getInterfaceRegistry(commandLine);
            final CipherKey projectCipherKey = Optional.ofNullable(commandLine.getOptionValue(CLIParameters.PROJECT_PATH_OPTION))
                                                       .map(Paths::get)
                                                       .map(OmpReader::readProjectCipherKey)
                                                       .orElseThrow(() -> new UnexpectedException("Provide a project location"));

            final CLUDevice device = fetchDevice(networkInterface, ipAddress, projectCipherKey, interfaceRegistry);
            // try (CLUClient client = new CLUClient(networkInterface, device, projectCipherKey)) {
            //     // NOP
            // }
        } else if (commandLine.hasOption(CLIParameters.EXECUTE_OPTION)) {
            final String command = commandLine.getOptionValue(CLIParameters.EXECUTE_OPTION);

            final CipherKey projectCipherKey = CLIParameters.getProjectCipherKey(commandLine)
                                                            .orElseThrow(() -> new UnexpectedException("Provide a project location"));

            try (CLUClient client = new CLUClient(networkInterface.getAddress(), ipAddress, projectCipherKey)) {
                LOGGER.info(client.execute(command).get());

                final Boolean success = client.startTFTPdServer().get();
                if (success) {
                    final CLUDevice device = client.getCluDevice();

                    final Path rootPath = Paths.get(".").resolve("live").resolve(String.valueOf(device.getSerialNumber()));
                    FileUtil.mkdir(rootPath);

                    for (CLUFiles cluLikeFile : CLUFiles.values()) {
                        if (!cluLikeFile.isReadable() || !cluLikeFile.isWritable()) {
                            continue;
                        }

                        final Path target = rootPath.resolve(cluLikeFile.getDevice() + "_" + StringUtils.lowerCase(cluLikeFile.getFileName()));

                        try {
                            client.downloadFile(cluLikeFile.getLocation(), target);
                        } catch (Exception e) {
                            FileUtil.deleteQuietly(target);
                        }
                    }
                }
            }
        }
    }

    private static void discover(
        NetworkInterfaceDto networkInterface,
        CipherKey projectCipherKey,
        Integer cluLimit,
        InterfaceRegistry interfaceRegistry
    ) {
        final List<Inet4Address> usedAddresses = new ArrayList<>();
        usedAddresses.add(networkInterface.getAddress());
        usedAddresses.add(MIN_IP);

        try (Client broadcastClient = new Client(networkInterface.getAddress())) {
            broadcastClient.discover(
                               projectCipherKey, PRIVATE_KEYS,
                               DEFAULT_LONG_TIMEOUT, cluLimit
                           )
                           .map(cluDevice -> {
                               LOGGER.debug("Discovered device: {}", cluDevice);

                               return new CLUClient(networkInterface.getAddress(), cluDevice);
                           })
                           .forEach(client -> {
                               try (client) {
                                   final CLUDevice device = client.getCluDevice();
                                   final Inet4Address deviceAddress = device.getAddress();

                                   // temporary hack, we expect the lowest ip to be last
                                   final Inet4Address lastAddress = usedAddresses.getLast();
                                   final Inet4Address nextAddress = networkInterface.nextAvailable(
                                                                                        lastAddress, Duration.ofMillis(TIMEOUT),
                                                                                        deviceAddress, usedAddresses
                                                                                    )
                                                                                    .get();

                                   usedAddresses.add(nextAddress);

                                   client.updateCipherKey(projectCipherKey)
                                         .get();

                                   if (!deviceAddress.equals(nextAddress)) {
                                       client.setAddress(nextAddress, networkInterface.getAddress())
                                             .map(address -> {
                                                 Util.repeatUntilTrueOrTimeout(
                                                     DEFAULT_LONG_TIMEOUT,
                                                     duration ->
                                                         Optional.of(
                                                             IPv4AddressUtil.ping(address)
                                                         )
                                                 );

                                                 return address;
                                             })
                                             .orElseGet(() -> {
                                                 LOGGER.warn("CLU did not accept new IP address");

                                                 return null;
                                             });
                                   }

                                   client.reset(DEFAULT_LONG_TIMEOUT)
                                         .get();

                                   Util.repeatUntilTrueOrTimeout(
                                           DEFAULT_LONG_TIMEOUT,
                                           duration ->
                                               client.checkAlive()
                                       )
                                       .orElseThrow(() -> new UnexpectedException("CLU did not came up alive"));

                                   detect(interfaceRegistry, client, device);
                               }
                           });
        }
    }

    private static void detect(InterfaceRegistry interfaceRegistry, CLUClient client, CLUDevice device) {
        client.startTFTPdServer()
              .get();

        final Path temporaryFile = FileUtil.temporaryFile();
        try {
            final Optional<Path> path = client.downloadFile(CLUFiles.CONFIG_JSON.getLocation(), temporaryFile);
            if (path.isPresent()) {
                final Path configJsonFile = path.get();
                final CLUDeviceConfig configJson = ObjectMapperFactory.JSON.readerFor(CLUDeviceConfig.class)
                                                                           .readValue(configJsonFile.toFile());

                LOGGER.info(device.toString());
                LOGGER.info(configJson.toString());

                final CLU cluDefinition = interfaceRegistry.getCLU(
                                                               configJson.getHardwareType(), configJson.getHardwareVersion(),
                                                               configJson.getFirmwareType(), configJson.getFirmwareVersion()
                                                           )
                                                           .get();

                LOGGER.info(cluDefinition.getTypeName());
            }
        } catch (IOException e) {
            throw new UnexpectedException(e);
        } finally {
            FileUtil.deleteQuietly(temporaryFile);
        }

        client.stopTFTPdServer()
              .get();
    }

    private static CLUDevice fetchDevice(
        NetworkInterfaceDto networkInterface, Inet4Address ipAddress,
        CipherKey projectCipherKey, InterfaceRegistry interfaceRegistry
    ) throws IOException {
        final CLUDevice device;
        try (CLUClient client = new CLUClient(networkInterface.getAddress(), ipAddress, projectCipherKey)) {
            client.startTFTPdServer()
                  .get();

            final Path temporaryFile = FileUtil.temporaryFile();
            try {
                final Optional<Path> path = client.downloadFile(CLUFiles.CONFIG_JSON.getLocation(), temporaryFile);
                if (path.isEmpty()) {
                    throw new UnexpectedException("Unrecognized CLU");
                }

                final CLUDeviceConfig configJson = ObjectMapperFactory.JSON.readerFor(CLUDeviceConfig.class)
                                                                           .readValue(path.get().toFile());

                device = new CLUDevice(
                    configJson.getSerialNumber(),
                    configJson.getMacAddress(),
                    ipAddress,
                    CipherTypeEnum.PROJECT
                );

                LOGGER.debug(device.toString());
                LOGGER.debug(configJson.toString());

                final CLU cluDefinition = interfaceRegistry.getCLU(
                                                               configJson.getHardwareType(), configJson.getHardwareVersion(),
                                                               configJson.getFirmwareType(), configJson.getFirmwareVersion()
                                                           )
                                                           .get();

                LOGGER.debug(cluDefinition.getTypeName());
            } finally {
                FileUtil.deleteQuietly(temporaryFile);
            }

            client.stopTFTPdServer()
                  .get();
        }

        return device;
    }
}
