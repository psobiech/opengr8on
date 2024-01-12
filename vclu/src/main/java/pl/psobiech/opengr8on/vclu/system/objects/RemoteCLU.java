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

package pl.psobiech.opengr8on.vclu.system.objects;

import java.net.Inet4Address;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.CLUClient;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.commands.LuaScriptCommand;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.ThreadUtil;

public class RemoteCLU extends VirtualObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCLU.class);

    public static final int INDEX = 1;

    private final ScheduledExecutorService executor;

    private final CLUClient client;

    public RemoteCLU(String name, Inet4Address address, Inet4Address localAddress, CipherKey cipherKey) {
        super(name);

        this.executor = ThreadUtil.virtualScheduler(name);
        this.client   = new CLUClient(localAddress, address, cipherKey);

        register(Methods.EXECUTE, args -> {
            final String script = args.checkjstring(1);

            if (script.startsWith(LuaScriptCommand.SET_VARS)) {
                executor.submit(() -> client.execute(script));
            } else {
                final Optional<String> execute = client.execute(script);
                if (execute.isPresent()) {
                    return LuaValue.valueOf(execute.get());
                }
            }

            return LuaValue.NIL;
        });
    }

    @Override
    public void close() {
        IOUtil.closeQuietly(client);
    }

    private enum Methods implements IMethod {
        EXECUTE(0),
        //
        ;

        private final int index;

        Methods(int index) {
            this.index = index;
        }

        @Override
        public int index() {
            return index;
        }
    }
}
