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

import java.net.Inet4Address;
import java.util.Optional;

import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.CLUClient;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.util.IPv4AddressUtil.NetworkInterfaceDto;

public class VirtualRemoteCLU extends VirtualObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualRemoteCLU.class);

    public VirtualRemoteCLU(String name, Inet4Address address, NetworkInterfaceDto networkInterface, CipherKey cipherKey) {
        super(name);

        funcs.put(0, args -> {
            final String script = String.valueOf(args.checkstring(1));

            try (CLUClient client = new CLUClient(networkInterface, address, cipherKey)) {
                final Optional<String> execute = client.execute(script);
                if (execute.isPresent()) {
                    return LuaValue.valueOf(execute.get());
                }
            }

            return LuaValue.NIL;
        });
    }
}
