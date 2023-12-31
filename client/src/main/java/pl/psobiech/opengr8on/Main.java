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

package pl.psobiech.opengr8on;

import java.io.IOException;
import java.net.Inet4Address;
import java.nio.file.Files;
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
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.CLUClient;
import pl.psobiech.opengr8on.client.CLUFiles;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.Client;
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
    public static final Duration DEFAULT_LONG_TIMEOUT = Duration.ofMillis(30_000);

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private static final Map<Long, byte[]> PRIVATE_KEYS = Map.of(
        0x0L, "00000000".getBytes()
    );

    private static final Inet4Address MIN_IP = IPv4AddressUtil.parseIPv4("10.72.144.1");

    public static void main(String[] args) throws Exception {
        final Option helpOption = Option.builder("h").longOpt("help")
                                        .desc("display current help")
                                        .build();

        final Option localIPAddressPathOption = Option.builder("nip").longOpt("network-address")
                                                      .desc("local IPv4 address to use")
                                                      .hasArg()
                                                      .build();

        final Option networkInterfaceOption = Option.builder("ni").longOpt("network-interface")
                                                    .desc("local network interface name")
                                                    .hasArg()
                                                    .build();

        //

        final Option projectPathOption = Option.builder("p").longOpt("project")
                                               .desc("OMP project file path")
                                               .hasArg()
                                               .build();

        //

        final Option discoverOption = Option.builder("d").longOpt("discover")
                                            .desc("discover clus")
                                            .build();

        final Option deviceInterfacesPathOption = Option.builder("di").longOpt("device-interfaces")
                                                        .desc("device interfaces directory path")
                                                        .hasArg()
                                                        .build();

        final Option cluLimitPathOption = Option.builder("cl").longOpt("clu-limit")
                                                .desc("maximum number of clus to discover")
                                                .hasArg()
                                                .build();

        //

        final Option fetchOption = Option.builder("f").longOpt("fetch")
                                         .desc("fetch device info")
                                         .build();

        final Option executeOption = Option.builder("e").longOpt("execute")
                                           .desc("execute command")
                                           .hasArg()
                                           .build();

        final Option ipAddressPathOption = Option.builder("ip").longOpt("address")
                                                 .desc("local IPv4 address to use")
                                                 .hasArg()
                                                 .build();

        //

        final Options options = new Options()
            .addOption(helpOption)
            .addOption(localIPAddressPathOption).addOption(networkInterfaceOption).addOption(projectPathOption)
            .addOption(discoverOption).addOption(deviceInterfacesPathOption).addOption(cluLimitPathOption)
            .addOption(ipAddressPathOption).addOption(fetchOption).addOption(executeOption);

        final CommandLine commandLine = new DefaultParser().parse(options, args);
        if (commandLine.hasOption(helpOption)) {
            new HelpFormatter()
                .printHelp("java -jar opengr8on.jar", options);

            System.exit(0);
        }

        //

        final NetworkInterfaceDto networkInterface = getNetworkInterface(commandLine, networkInterfaceOption, localIPAddressPathOption);
        LOGGER.debug("Using network interface: {}", networkInterface);

        final InterfaceRegistry interfaceRegistry = Optional.ofNullable(commandLine.getOptionValue(deviceInterfacesPathOption))
                                                            .map(Paths::get)
                                                            .map(InterfaceRegistry::new)
                                                            .orElseGet(() -> new InterfaceRegistry(Paths.get("./device-interfaces")));

        if (commandLine.hasOption(discoverOption)) {
            final CipherKey projectCipherKey = Optional.ofNullable(commandLine.getOptionValue(projectPathOption))
                                                       .map(Paths::get)
                                                       .map(OmpReader::readProjectCipherKey)
                                                       .orElseGet(() -> {
                                                           final CipherKey cipherKey = new CipherKey(RandomUtil.bytes(16), RandomUtil.bytes(16));
                                                           LOGGER.debug("Generated random project key: {}", cipherKey);

                                                           return cipherKey;
                                                       });

            final Integer cluLimit = Optional.ofNullable(commandLine.getOptionValue(cluLimitPathOption))
                                             .map(Integer::parseInt)
                                             .orElse(Integer.MAX_VALUE);

            discover(networkInterface, projectCipherKey, cluLimit, interfaceRegistry);

            return;
        }

        final Inet4Address ipAddress = Optional.ofNullable(commandLine.getOptionValue(ipAddressPathOption))
                                               .map(IPv4AddressUtil::parseIPv4)
                                               .orElseThrow(() -> new UnexpectedException("Missing device IP address"));

        if (commandLine.hasOption(fetchOption)) {
            final CipherKey projectCipherKey = Optional.ofNullable(commandLine.getOptionValue(projectPathOption))
                                                       .map(Paths::get)
                                                       .map(OmpReader::readProjectCipherKey)
                                                       .orElseThrow(() -> new UnexpectedException("Provide a project location"));

            final CLUDevice device;
            try (CLUClient client = new CLUClient(networkInterface, ipAddress, projectCipherKey)) {
                client.startTFTPdServer()
                      .get();

                final Path temporaryFile = FileUtil.temporaryFile();
                try {
                    final Optional<Path> path = client.downloadFile(CLUFiles.CONFIG_JSON.getLocation(), temporaryFile);
                    if (path.isEmpty()) {
                        throw new UnexpectedException("Unrecognized CLU");
                    }

                    final Path configJsonFile = path.get();
                    final CLUDeviceConfig configJson = ObjectMapperFactory.JSON.readerFor(CLUDeviceConfig.class)
                                                                               .readValue(configJsonFile.toFile());

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

            try (CLUClient client = new CLUClient(networkInterface, device, projectCipherKey)) {
                // NOP
            }
        } else if (commandLine.hasOption(executeOption)) {
            final String command = commandLine.getOptionValue(executeOption);

            final CipherKey projectCipherKey = Optional.ofNullable(commandLine.getOptionValue(projectPathOption))
                                                       .map(Paths::get)
                                                       .map(OmpReader::readProjectCipherKey)
                                                       .orElseThrow(() -> new UnexpectedException("Provide a project location"));

            try (CLUClient client = new CLUClient(networkInterface, ipAddress, projectCipherKey)) {
                LOGGER.info(client.execute(command).get());

                final Boolean success = client.startTFTPdServer().get();
                if (success) {
                    final CLUDevice device = client.getCluDevice();

                    final Path rootPath = Paths.get(".").resolve("live").resolve(String.valueOf(device.getSerialNumber()));
                    Files.createDirectories(rootPath);

                    for (CLUFiles cluLikeFile : CLUFiles.values()) {
                        if (!cluLikeFile.isReadable() || !cluLikeFile.isWritable()) {
                            continue;
                        }

                        final Path target = rootPath.resolve(cluLikeFile.getDevice() + "_" + StringUtils.lowerCase(cluLikeFile.getFileName()));

                        try {
                            System.out.println(target);
                            client.downloadFile(cluLikeFile.getLocation(), target);
                        } catch (Exception e) {
                            FileUtil.deleteQuietly(target);
                        }
                    }
                }
            }
        }
    }

    private static NetworkInterfaceDto getNetworkInterface(CommandLine commandLine, Option networkInterfaceOption, Option localIPAddressPathOption) {
        final Optional<String> ipAddressOptional = Optional.ofNullable(commandLine.getOptionValue(localIPAddressPathOption));
        if (ipAddressOptional.isPresent()) {
            final String ipAddress = ipAddressOptional.get();

            final Optional<NetworkInterfaceDto> networkInterfaceByAddressOptional = IPv4AddressUtil.getLocalIPv4NetworkInterfaceByIpAddress(ipAddress);
            if (networkInterfaceByAddressOptional.isEmpty()) {
                throw new UnexpectedException("Could not find network interface with address: " + ipAddress);
            }

            final Optional<String> networkInterfaceNameOptional = Optional.ofNullable(commandLine.getOptionValue(networkInterfaceOption));
            if (networkInterfaceNameOptional.isPresent()) {
                final String networkInterfaceName = networkInterfaceNameOptional.get();
                final String ipAddressNetworkInterfaceName = networkInterfaceByAddressOptional.get().getNetworkInterface().getName();
                if (!networkInterfaceName.equals(ipAddressNetworkInterfaceName)) {
                    throw new UnexpectedException(
                        "Network interface %s does not have address %s configured"
                            .formatted(
                                networkInterfaceName,
                                ipAddressNetworkInterfaceName
                            )
                    );
                }
            }

            return networkInterfaceByAddressOptional.get();
        }

        final Optional<String> networkInterfaceNameOptional = Optional.ofNullable(commandLine.getOptionValue(networkInterfaceOption));
        if (networkInterfaceNameOptional.isPresent()) {
            final String networkInterfaceName = networkInterfaceNameOptional.get();

            final Optional<NetworkInterfaceDto> networkInterfaceByName = IPv4AddressUtil.getLocalIPv4NetworkInterfaceByName(networkInterfaceName);
            if (networkInterfaceByName.isEmpty()) {
                throw new UnexpectedException("Could not find local network interface with name: " + networkInterfaceName);
            }

            return networkInterfaceByName.get();
        }

        throw new UnexpectedException("Provide either address or network interface name");
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

        try (Client broadcastClient = new Client(networkInterface)) {
            broadcastClient.discover(
                               projectCipherKey, PRIVATE_KEYS,
                               DEFAULT_LONG_TIMEOUT, cluLimit
                           )
                           .map(cluDevice -> {
                               LOGGER.debug("Discovered device: {}", cluDevice);

                               return new CLUClient(networkInterface, cluDevice);
                           })
                           .forEach(client -> {
                               try (client) {
                                   final CLUDevice device = client.getCluDevice();
                                   final Inet4Address deviceAddress = device.getAddress();

                                   // temporary hack, we expect the lowest ip to be last
                                   final Inet4Address lastAddress = usedAddresses.getLast();
                                   final Inet4Address nextAddress = networkInterface.nextAvailable(
                                                                                        lastAddress, Duration.ofMillis(4_000),
                                                                                        deviceAddress, usedAddresses
                                                                                    )
                                                                                    .get();

                                   usedAddresses.add(nextAddress);

                                   client.setCipherKey(projectCipherKey)
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
                                             .orElseThrow(() -> new UnexpectedException("CLU did not accept new IP address"));
                                   }

                                   client.reset(DEFAULT_LONG_TIMEOUT)
                                         .get();

                                   Util.repeatUntilTrueOrTimeout(
                                           DEFAULT_LONG_TIMEOUT,
                                           duration ->
                                               client.checkAlive()
                                       )
                                       .orElseThrow(() -> new UnexpectedException("CLU did not came up alive"));

                                   client.startTFTPdServer()
                                         .get();

                                   final Path temporaryFile = FileUtil.temporaryFile();
                                   try {
                                       final Optional<Path> path = client.downloadFile(CLUFiles.CONFIG_JSON.getLocation(), temporaryFile);
                                       if (path.isPresent()) {
                                           final Path configJsonFile = path.get();
                                           final CLUDeviceConfig configJson = ObjectMapperFactory.JSON.readerFor(CLUDeviceConfig.class)
                                                                                                      .readValue(configJsonFile.toFile());

                                           LOGGER.debug(device.toString());
                                           LOGGER.debug(configJson.toString());

                                           final CLU cluDefinition = interfaceRegistry.getCLU(
                                                                                          configJson.getHardwareType(), configJson.getHardwareVersion(),
                                                                                          configJson.getFirmwareType(), configJson.getFirmwareVersion()
                                                                                      )
                                                                                      .get();

                                           LOGGER.debug(cluDefinition.getTypeName());
                                       }
                                   } catch (IOException e) {
                                       throw new UnexpectedException(e);
                                   } finally {
                                       FileUtil.deleteQuietly(temporaryFile);
                                   }

                                   client.stopTFTPdServer()
                                         .get();
                               }
                           });
        }
    }
}
