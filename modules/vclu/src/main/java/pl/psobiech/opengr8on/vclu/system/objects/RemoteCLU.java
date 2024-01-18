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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.CLUClient;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.commands.LuaScriptCommand;
import pl.psobiech.opengr8on.exceptions.UncheckedInterruptedException;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.ThreadUtil;

public class RemoteCLU extends VirtualObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCLU.class);

    public static final int INDEX = 1;

    private final ExecutorService executor;

    private final CLUClient client;

    public RemoteCLU(String name, Inet4Address address, Inet4Address localAddress, CipherKey cipherKey, int port) {
        super(
            name,
            IFeature.EMPTY.class, Methods.class, IEvent.EMPTY.class
        );

        this.executor = ThreadUtil.daemonExecutor(name);

        this.client = new CLUClient(localAddress, address, cipherKey, port);

        register(Methods.EXECUTE, arg1 -> {
            final String script = arg1.checkjstring();

            if (script.startsWith(LuaScriptCommand.SET_VARS)) {
                executor.submit(() -> client.execute(script));
            } else {
                final Optional<String> returnValueOptional;
                try {
                    returnValueOptional = executor.submit(() ->
                                                      client.execute(script)
                                                  )
                                                  .get();
                } catch (InterruptedException e) {
                    throw new UncheckedInterruptedException(e);
                } catch (ExecutionException e) {
                    throw new UnexpectedException(e.getCause());
                }

                if (returnValueOptional.isPresent()) {
                    return LuaValue.valueOf(returnValueOptional.get());
                }
            }

            return LuaValue.NIL;
        });
    }

    @Override
    public void close() {
        super.close();

        ThreadUtil.closeQuietly(executor);
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
