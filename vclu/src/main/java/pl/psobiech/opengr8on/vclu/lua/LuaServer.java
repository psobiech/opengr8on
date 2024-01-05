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

package pl.psobiech.opengr8on.vclu.lua;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.CoroutineLib;
import org.luaj.vm2.lib.LibFunction;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.jse.JseBaseLib;
import org.luaj.vm2.lib.jse.JseOsLib;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.CLUFiles;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.exceptions.UncheckedInterruptedException;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.vclu.VirtualSystem;
import pl.psobiech.opengr8on.vclu.lua.fn.BaseLuaFunction;
import pl.psobiech.opengr8on.vclu.lua.fn.LuaNoArgConsumer;
import pl.psobiech.opengr8on.vclu.lua.fn.LuaThreeArgConsumer;
import pl.psobiech.opengr8on.vclu.lua.fn.LuaThreeArgFunction;
import pl.psobiech.opengr8on.vclu.lua.fn.LuaTwoArgFunction;
import pl.psobiech.opengr8on.vclu.lua.fn.LuaVarArgConsumer;
import pl.psobiech.opengr8on.vclu.lua.fn.LuaVarArgFunction;

public class LuaServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(LuaServer.class);

    private LuaServer() {
        // NOP
    }

    public static LibFunction wrap(Logger logger, LuaNoArgConsumer luaFunction) {
        return wrap(logger, (BaseLuaFunction) luaFunction);
    }

    public static LibFunction wrap(Logger logger, LuaThreeArgConsumer luaFunction) {
        return wrap(logger, (BaseLuaFunction) luaFunction);
    }

    public static LibFunction wrap(Logger logger, LuaVarArgConsumer luaFunction) {
        return wrap(logger, (BaseLuaFunction) luaFunction);
    }

    public static LibFunction wrap(Logger logger, LuaVarArgFunction luaFunction) {
        return wrap(logger, (BaseLuaFunction) luaFunction);
    }

    public static LibFunction wrap(Logger logger, LuaTwoArgFunction luaFunction) {
        return wrap(logger, (BaseLuaFunction) luaFunction);
    }

    public static LibFunction wrap(Logger logger, LuaThreeArgFunction luaFunction) {
        return wrap(logger, (BaseLuaFunction) luaFunction);
    }

    private static LibFunction wrap(Logger logger, BaseLuaFunction luaFunction) {
        return new LuaFunctionWrapper(logger, luaFunction);
    }

    public static MainThreadWrapper create(
        Path aDriveDirectory, CLUDevice cluDevice, CipherKey cipherKey, CLUFiles cluFile
    ) {
        final VirtualSystem virtualSystem = new VirtualSystem(
            aDriveDirectory,
            cluDevice.getAddress(),
            cluDevice, cipherKey
        );

        final Globals globals = new Globals();
        LoadState.install(globals);
        LuaC.install(globals);

        globals.load(new JseBaseLib());
        globals.load(new PackageLib());
        // globals.load(new Bit32Lib());
        globals.load(new TableLib());
        globals.load(new StringLib());
        globals.load(new CoroutineLib());
        // globals.load(new JseMathLib());
        // globals.load(new JseIoLib());
        globals.load(new JseOsLib());
        // globals.load(new LuajavaLib());
        globals.load(new LuaApiLib(LOGGER, virtualSystem, globals));

        globals.finder = fileName -> {
            try {
                final Path filePath = aDriveDirectory.resolve(StringUtils.upperCase(fileName));
                if (!filePath.startsWith(aDriveDirectory)) {
                    throw new UnexpectedException("Attempt to access external directory");
                }

                return Files.newInputStream(filePath);
            } catch (IOException e) {
                throw new UnexpectedException(e);
            }
        };

        loadScript(globals, aDriveDirectory, CLUFiles.INIT_LUA);

        return new MainThreadWrapper(
            virtualSystem, globals, aDriveDirectory, cluFile
        );
    }

    private static LuaValue loadScript(Globals globals, Path aDriveDirectory, CLUFiles cluFiles) {
        final String fileName = cluFiles.getFileName();

        return loadScript(globals, aDriveDirectory.resolve(fileName), fileName);
    }

    private static LuaValue loadScript(Globals globals, Path path, String name) {
        final String script;
        try {
            script = Files.readString(path);
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }

        return globals.load(script, name)
                      .call();
    }

    public static class MainThreadWrapper implements Closeable {
        private final VirtualSystem virtualSystem;

        private final Globals globals;

        private final Thread thread;

        public MainThreadWrapper(VirtualSystem virtualSystem, Globals globals, Path aDriveDirectory, CLUFiles cluFile) {
            this.thread = Thread.ofVirtual()
                                .name(getClass().getSimpleName())
                                .unstarted(
                                    () -> {
                                        try {
                                            loadScript(globals, aDriveDirectory, cluFile);
                                        } catch (LuaError e) {
                                            if (e.getCause() instanceof UncheckedInterruptedException) {
                                                LOGGER.trace(e.getMessage(), e);

                                                return;
                                            }

                                            LOGGER.error(e.getMessage(), e);
                                        } catch (Exception e) {
                                            LOGGER.error(e.getMessage(), e);
                                        }
                                    }
                                );

            this.virtualSystem = virtualSystem;
            this.globals       = globals;
        }

        public Globals globals() {
            return globals;
        }

        public VirtualSystem virtualSystem() {
            return virtualSystem;
        }

        public void start() {
            thread.start();
        }

        @Override
        public void close() {
            virtualSystem.close();

            thread.interrupt();

            try {
                thread.join();
            } catch (InterruptedException e) {
                LOGGER.warn(e.getMessage(), e);
            }
        }
    }

    private static class LuaFunctionWrapper extends LibFunction {
        private final Logger logger;

        private final BaseLuaFunction fn;

        LuaFunctionWrapper(Logger logger, BaseLuaFunction fn) {
            this.logger = logger;
            this.fn     = fn;
        }

        @Override
        public LuaValue call() {
            return invoke(LuaValue.NIL);
        }

        @Override
        public LuaValue call(LuaValue a) {
            return invoke(a);
        }

        @Override
        public LuaValue call(LuaValue a, LuaValue b) {
            return invoke(LuaValue.varargsOf(a, b));
        }

        @Override
        public LuaValue call(LuaValue a, LuaValue b, LuaValue c) {
            return invoke(LuaValue.varargsOf(a, b, c));
        }

        @Override
        public LuaValue call(LuaValue a, LuaValue b, LuaValue c, LuaValue d) {
            final LuaValue[] luaValues = {a, b, c, d};

            return invoke(LuaValue.varargsOf(luaValues, 0, luaValues.length));
        }

        @Override
        public LuaValue invoke(Varargs args) {
            try {
                return fn.invoke(args);
            } catch (UncheckedInterruptedException e) {
                logger.trace(e.getMessage(), e);

                throw e;
            } catch (Exception e) {
                logger.error(e.getMessage(), e);

                throw e;
            }
        }
    }
}
