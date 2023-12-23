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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.CoroutineLib;
import org.luaj.vm2.lib.LibFunction;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.jse.JseBaseLib;
import org.luaj.vm2.lib.jse.JseOsLib;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.CLUFiles;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.IPv4AddressUtil.NetworkInterfaceDto;
import pl.psobiech.opengr8on.vclu.VirtualSystem.Subscription;

import static org.apache.commons.lang3.StringUtils.lowerCase;
import static org.apache.commons.lang3.StringUtils.stripToEmpty;
import static org.apache.commons.lang3.StringUtils.stripToNull;

public class LuaServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(LuaServer.class);

    private LuaServer() {
        // NOP
    }

    public static LuaThreadWrapper create(NetworkInterfaceDto networkInterface, Path rootDirectory, CLUDevice cluDevice, CipherKey cipherKey) {
        final VirtualSystem virtualSystem = new VirtualSystem(networkInterface, cluDevice, cipherKey);

        Globals globals = new Globals();
        globals.load(new JseBaseLib());
        globals.load(new PackageLib());
        //globals.load(new Bit32Lib());
        globals.load(new TableLib());
        globals.load(new StringLib());
        globals.load(new CoroutineLib());
        //globals.load(new JseMathLib());
        //globals.load(new JseIoLib());
        globals.load(new JseOsLib());
        //globals.load(new LuajavaLib());

        globals.load(new TwoArgFunction() {
            public LuaValue call(LuaValue modname, LuaValue env) {
                final LuaValue library = tableOf();

                library.set("setup", new LibFunction() {
                    @Override
                    public LuaValue call() {
                        virtualSystem.setup();

                        return LuaValue.NIL;
                    }
                });

                library.set("loop", new LibFunction() {
                    @Override
                    public LuaValue call() {
                        virtualSystem.loop();

                        return LuaValue.NIL;
                    }
                });

                library.set("sleep", new OneArgFunction() {
                    @Override
                    public LuaValue call(LuaValue arg) {
                        virtualSystem.sleep(arg.checklong());

                        return LuaValue.NIL;
                    }
                });

                library.set("logDebug", new OneArgFunction() {
                    @Override
                    public LuaValue call(LuaValue arg) {
                        LOGGER.debug(String.valueOf(arg.checkstring()));

                        return LuaValue.NIL;
                    }
                });

                library.set("logInfo", new OneArgFunction() {
                    @Override
                    public LuaValue call(LuaValue arg) {
                        LOGGER.info(String.valueOf(arg.checkstring()));

                        return LuaValue.NIL;
                    }
                });

                library.set("logWarning", new OneArgFunction() {
                    @Override
                    public LuaValue call(LuaValue arg) {
                        LOGGER.warn(String.valueOf(arg.checkstring()));

                        return LuaValue.NIL;
                    }
                });

                library.set("logError", new OneArgFunction() {
                    @Override
                    public LuaValue call(LuaValue arg) {
                        LOGGER.error(String.valueOf(arg.checkstring()));

                        return LuaValue.NIL;
                    }
                });

                library.set("clientRegister", new LibFunction() {
                    @Override
                    public LuaValue invoke(Varargs args) {
                        final LuaValue object = args.arg(4);

                        final ArrayList<Subscription> subscriptions = new ArrayList<>();
                        if (!object.istable()) {
                            return LuaValue.valueOf("values:{" + 99 + "}");
                        }
                        final LuaTable checktable = object.checktable();
                        for (LuaValue key : checktable.keys()) {
                            final LuaValue keyValue = checktable.get(key);
                            if (!keyValue.istable()) {
                                return LuaValue.valueOf("values:{" + 99 + "}");
                            }

                            subscriptions.add(
                                new Subscription(
                                    String.valueOf(keyValue.checktable().get(1).checktable().get("name")),
                                    keyValue.checktable().get(2).checkint()
                                )
                            );
                        }

                        return LuaValue.valueOf(
                            virtualSystem.clientRegister(
                                String.valueOf(args.arg1().checkstring()),
                                args.arg(2).checkint(),
                                args.arg(3).checkint(),
                                subscriptions
                            )
                        );
                    }
                });

                library.set("clientDestroy", new LibFunction() {
                    @Override
                    public LuaValue invoke(Varargs args) {
                        return virtualSystem.clientDestroy(
                            String.valueOf(args.arg1().checkstring()),
                            args.arg(2).checkint(),
                            args.arg(3).checkint()
                        );
                    }
                });

                library.set("fetchValues", new OneArgFunction() {
                    @Override
                    public LuaValue call(LuaValue object) {
                        final ArrayList<Subscription> subscriptions = new ArrayList<>();
                        if (!object.istable()) {
                            return LuaValue.valueOf("values:{" + 99 + "}");
                        }

                        final LuaTable checktable = object.checktable();
                        for (LuaValue key : checktable.keys()) {
                            final LuaValue keyValue = checktable.get(key);
                            if (!keyValue.istable()) {
                                return LuaValue.valueOf("values:{\"" + globals.load("return _G[\"%s\"]".formatted(keyValue)).call() + "\"}");
                            }

                            subscriptions.add(
                                new Subscription(
                                    String.valueOf(keyValue.checktable().get(1).checktable().get("name")),
                                    keyValue.checktable().get(2).checkint()
                                )
                            );
                        }

                        return LuaValue.valueOf(
                            "values:" + virtualSystem.fetchValues(
                                subscriptions
                            )
                        );
                    }

                    @Override
                    public LuaValue call(LuaValue object, LuaValue arg1, LuaValue arg2) {
                        virtualSystem.getObject(object.checkint()).addEvent(arg1.checkint(), arg2.checkfunction());

                        return LuaValue.NIL;
                    }
                });

                library.set("newObject", new ThreeArgFunction() {
                    @Override
                    public LuaValue call(LuaValue arg1, LuaValue arg2, LuaValue arg3) {
                        return LuaValue.valueOf(
                            virtualSystem.newObject(arg1.checkint(), String.valueOf(arg2.checkstring()), arg3.checkint())
                        );
                    }
                });

                library.set("newGate", new ThreeArgFunction() {
                    @Override
                    public LuaValue call(LuaValue arg1, LuaValue arg2, LuaValue arg3) {
                        return LuaValue.valueOf(
                            virtualSystem.newGate(arg1.checkint(), String.valueOf(arg2.checkstring()))
                        );
                    }
                });

                library.set("get", new TwoArgFunction() {
                    @Override
                    public LuaValue call(LuaValue object, LuaValue arg) {
                        return virtualSystem.getObject(object.checkint()).get(arg.checkint());
                    }
                });

                library.set("set", new ThreeArgFunction() {
                    @Override
                    public LuaValue call(LuaValue object, LuaValue arg1, LuaValue arg2) {
                        virtualSystem.getObject(object.checkint()).set(arg1.checkint(), arg2);

                        return LuaValue.NIL;
                    }
                });

                library.set("execute", new ThreeArgFunction() {
                    @Override
                    public LuaValue call(LuaValue object, LuaValue arg1, LuaValue arg2) {
                        return virtualSystem.getObject(object.checkint()).execute(arg1.checkint(), arg2);
                    }
                });

                library.set("addEvent", new ThreeArgFunction() {
                    @Override
                    public LuaValue call(LuaValue object, LuaValue arg1, LuaValue arg2) {
                        virtualSystem.getObject(object.checkint()).addEvent(arg1.checkint(), arg2.checkfunction());

                        return LuaValue.NIL;
                    }
                });

                env.set("api", library);

                return library;
            }
        });

        LoadState.install(globals);
        LuaC.install(globals);

        globals.finder = filename -> {
            try {
                final Path filePath = rootDirectory.resolve(StringUtils.upperCase(filename));
                if (!filePath.getParent().equals(rootDirectory)) {
                    throw new UnexpectedException("Attempt to access external directory");
                }

                return Files.newInputStream(
                    filePath
                );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        loadScript(globals, classPath(URI.create("classpath:/INIT.LUA")), "INIT.LUA");

        return new LuaThreadWrapper(
            globals, rootDirectory
        );
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

    private static final String JAR_PATH_SEPARATOR = Pattern.quote("!");

    private static Path classPath(URI uri) {
        final String resourceUriPath = getResourceUriPath(uri);

        final URL url = LuaServer.class.getResource(resourceUriPath);
        if (url == null) {
            throw new UnexpectedException(uri + " not found!");
        }

        try {
            final URI classPathUri = url.toURI();
            final String scheme = classPathUri.getScheme();
            final String classPathUriAsString = classPathUri.toString();

            final Path path;
            if (scheme.equals(SchemeEnum.JAR.toUrlScheme())) {
                final String jarPath = classPathUriAsString.split(JAR_PATH_SEPARATOR, 2)[0];

                path = getOrCreateJarFileSystemFor(jarPath).provider()
                                                           .getPath(classPathUri);
            } else {
                path = Paths.get(classPathUri);
            }

            return path;
        } catch (URISyntaxException e) {
            throw new UnexpectedException(e);
        }
    }

    private static final Map<String, FileSystem> jarFileSystems = new HashMap<>();

    private static FileSystem getOrCreateJarFileSystemFor(String jarPath) {
        synchronized (jarFileSystems) {
            return jarFileSystems.computeIfAbsent(
                jarPath,
                ignored -> {
                    try {
                        final URI jarUri = URI.create(jarPath);
                        System.out.println(jarUri);
                        return FileSystems.newFileSystem(jarUri, Collections.emptyMap());
                    } catch (IOException e) {
                        throw new UnexpectedException(e);
                    }
                }
            );
        }
    }

    private static String getResourceUriPath(URI uri) {
        final String path = stripToEmpty(uri.getPath());

        final String host = stripToNull(uri.getHost());
        if (host == null) {
            return path;
        }

        return "/" + host + path;
    }

    public enum SchemeEnum {
        CLASSPATH,
        JAR,
        FILE,
        //
        ;

        public String toUrlScheme() {
            return lowerCase(name());
        }

        public static SchemeEnum fromUrlScheme(String urlScheme) {
            if (urlScheme == null) {
                return FILE;
            }

            for (SchemeEnum scheme : values()) {
                if (scheme.toUrlScheme().equals(urlScheme)) {
                    return scheme;
                }
            }

            throw new UnexpectedException(String.format("Unsupported resource scheme: %s", urlScheme));
        }
    }

    public static class LuaThreadWrapper extends Thread {
        private final Globals globals;

        public LuaThreadWrapper(Globals globals, Path rootDirectory) {
            super(() -> {
                final LuaValue mainChunk = loadScript(globals, rootDirectory.resolve(CLUFiles.MAIN_LUA.getFileName()), CLUFiles.MAIN_LUA.getFileName());

                final LuaThread luaThread = new LuaThread(globals, mainChunk);

                luaThread.resume(LuaValue.NIL);
            });

            setDaemon(true);

            this.globals = globals;

            start();
        }

        public Globals globals() {
            return globals;
        }

    }
}
