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

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.CoroutineLib;
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
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.IPv4AddressUtil.NetworkInterfaceDto;

import static org.apache.commons.lang3.StringUtils.lowerCase;
import static org.apache.commons.lang3.StringUtils.stripToEmpty;
import static org.apache.commons.lang3.StringUtils.stripToNull;

public class LuaServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(LuaServer.class);

    private LuaServer() {
        // NOP
    }

    public static LuaThreadWrapper create(NetworkInterfaceDto networkInterface, Path aDriveDirectory, CLUDevice cluDevice, CipherKey cipherKey) {
        final VirtualSystem virtualSystem = new VirtualSystem(
            networkInterface,
            cluDevice, cipherKey
        );

        Globals globals = new Globals();
        LoadState.install(globals);
        LuaC.install(globals);

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
        globals.load(new CluLuaApi(virtualSystem, globals));

        globals.finder = fileName -> {
            try {
                final Path filePath = aDriveDirectory.resolve(StringUtils.upperCase(fileName));
                if (!filePath.getParent().equals(aDriveDirectory)) {
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
            virtualSystem, globals, aDriveDirectory
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
    }

    public static class LuaThreadWrapper extends Thread implements Closeable {
        private final VirtualSystem virtualSystem;

        private final Globals globals;

        public LuaThreadWrapper(VirtualSystem virtualSystem, Globals globals, Path aDriveDirectory) {
            super(() -> loadScript(globals, aDriveDirectory.resolve(CLUFiles.MAIN_LUA.getFileName()), CLUFiles.MAIN_LUA.getFileName()));

            setDaemon(true);

            this.virtualSystem = virtualSystem;
            this.globals = globals;
        }

        public Globals globals() {
            return globals;
        }

        @Override
        public void close() {
            virtualSystem.close();

            interrupt();

            try {
                join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                throw new UnexpectedException(e);
            }
        }
    }
}
