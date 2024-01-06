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

import java.net.Inet4Address;
import java.nio.file.Paths;
import java.util.Optional;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil.NetworkInterfaceDto;
import pl.psobiech.opengr8on.xml.interfaces.InterfaceRegistry;
import pl.psobiech.opengr8on.xml.omp.OmpReader;

public class CLIParameters {
    private CLIParameters() {
        // NOP
    }

    static final Option HELP_OPTION = Option.builder("h").longOpt("help")
                                            .desc("display current help")
                                            .build();

    static final Option NETWORK_INTERFACE_OPTION = Option.builder("i").longOpt("interface")
                                                         .desc("local network interface name or local IPv4 address to use")
                                                         .hasArg()
                                                         .build();

    static final Option REMOTE_ADDRESS_PATH_OPTION = Option.builder("a").longOpt("address")
                                                           .desc("remote CLU IPv4 address")
                                                           .hasArg()
                                                           .build();

    static final Option PROJECT_PATH_OPTION = Option.builder("p").longOpt("project")
                                                    .desc(".omp project file path")
                                                    .hasArg()
                                                    .build();

    static final Option DISCOVER_OPTION = Option.builder("d").longOpt("discover")
                                                .desc("discover clus")
                                                .build();

    static final Option DEVICE_INTERFACES_PATH_OPTION = Option.builder("di").longOpt("device-interfaces")
                                                              .desc("device interfaces directory path")
                                                              .hasArg()
                                                              .build();

    static final Option CLU_LIMIT_PATH_OPTION = Option.builder("l").longOpt("limit")
                                                      .desc("maximum number of CLUs to discover (default 1)")
                                                      .hasArg()
                                                      .build();

    static final Option FETCH_OPTION = Option.builder("f").longOpt("fetch")
                                             .desc("fetch device info")
                                             .build();

    static final Option EXECUTE_OPTION = Option.builder("e").longOpt("execute")
                                               .desc("execute command")
                                               .hasArg()
                                               .build();

    static final Options OPTIONS = new Options().addOption(HELP_OPTION)
                                                .addOption(NETWORK_INTERFACE_OPTION).addOption(PROJECT_PATH_OPTION)
                                                .addOption(DISCOVER_OPTION).addOption(DEVICE_INTERFACES_PATH_OPTION).addOption(CLU_LIMIT_PATH_OPTION)
                                                .addOption(REMOTE_ADDRESS_PATH_OPTION).addOption(FETCH_OPTION).addOption(EXECUTE_OPTION);

    static InterfaceRegistry getInterfaceRegistry(CommandLine commandLine) {
        return Optional.ofNullable(commandLine.getOptionValue(DEVICE_INTERFACES_PATH_OPTION))
                       .map(Paths::get)
                       .map(InterfaceRegistry::new)
                       .orElseGet(() -> new InterfaceRegistry(Paths.get("./device-interfaces")));
    }

    static Optional<CipherKey> getProjectCipherKey(CommandLine commandLine) {
        return Optional.ofNullable(commandLine.getOptionValue(PROJECT_PATH_OPTION))
                       .map(Paths::get)
                       .map(OmpReader::readProjectCipherKey);
    }

    static NetworkInterfaceDto getNetworkInterface(CommandLine commandLine) {
        final Optional<String> addressOrNetworkInterfaceOptional = Optional.ofNullable(commandLine.getOptionValue(NETWORK_INTERFACE_OPTION));
        if (addressOrNetworkInterfaceOptional.isPresent()) {
            final String networkInterfaceNameOrIpAddress = addressOrNetworkInterfaceOptional.get();

            return IPv4AddressUtil.getLocalIPv4NetworkInterfaceByNameOrAddress(networkInterfaceNameOrIpAddress)
                                  .orElseThrow(() -> new UnexpectedException("Could not find network interface / address: " + networkInterfaceNameOrIpAddress));
        }

        throw new UnexpectedException("Provide address / network interface name (eg. -i 192.168.1.100)");
    }

    static Inet4Address getRemoteIPAddress(CommandLine commandLine) {
        return Optional.ofNullable(commandLine.getOptionValue(REMOTE_ADDRESS_PATH_OPTION))
                       .map(IPv4AddressUtil::parseIPv4)
                       .orElseThrow(() -> new UnexpectedException("Missing remote device IP address"));
    }
}
