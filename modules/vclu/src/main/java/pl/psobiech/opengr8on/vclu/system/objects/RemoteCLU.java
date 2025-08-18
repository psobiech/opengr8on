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

import org.apache.commons.lang3.StringUtils;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.compiler.LuaC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.CLUClient;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.vclu.system.VirtualSystem;

public class RemoteCLU extends VirtualObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCLU.class);

    public static final int INDEX = 1;

    private final CLUClient client;

    private final Globals localLuaContext;

    public RemoteCLU(VirtualSystem virtualSystem, String name, Inet4Address address, Inet4Address localAddress, CipherKey cipherKey, int port) {
        super(
                virtualSystem, name,
                IFeature.EMPTY.class, Methods.class, IEvent.EMPTY.class,
                ThreadUtil::virtualScheduler
        );

        this.localLuaContext = new Globals();
        // LoadState.install(globals);
        LuaC.install(localLuaContext);

        this.client = new CLUClient(localAddress, address, cipherKey, port);

        register(Methods.EXECUTE, arg1 -> {
            final String script = arg1.checkjstring();

            return remoteExecute(script);
        });
    }

    public LuaValue remoteExecute(String script) {
        return client.execute(script)
                .map(returnValue -> {
                            returnValue = StringUtils.stripToNull(returnValue);
                            if (returnValue == null) {
                                return null;
                            }

                            if (returnValue.startsWith("{")) {
                                try {
                                    return localLuaContext.load("return %s".formatted(returnValue))
                                            .call();
                                } catch (Exception e) {
                                    // Might not have been a proper LUA table
                                    // TODO: implement a more robust check

                                    LOGGER.error(e.getMessage(), e);
                                }
                            }

                            final LuaString luaString = LuaValue.valueOf(returnValue);
                            if (luaString.isnumber()) {
                                return luaString.checknumber();
                            }

                            return luaString;
                        }
                )
                .orElse(LuaValue.NIL);
    }

    @Override
    public void close() {
        super.close();

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
