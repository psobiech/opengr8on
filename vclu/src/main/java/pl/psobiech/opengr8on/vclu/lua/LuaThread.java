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

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.jse.JseBaseLib;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import pl.psobiech.opengr8on.client.CLUFiles;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.Slf4jLoggingOutputStream;
import pl.psobiech.opengr8on.vclu.VirtualSystem;

public class LuaThread {
    private static final Logger LOGGER = LoggerFactory.getLogger(LuaThread.class);

    private static final Logger LOGGER_STDOUT = LoggerFactory.getLogger(LuaThread.class.getName() + "$stdout");

    private LuaThread() {
        // NOP
    }

    public static LuaThreadWrapper create(
        Path aDriveDirectory, CLUDevice cluDevice, CipherKey cipherKey, CLUFiles cluFile
    ) {
        final VirtualSystem virtualSystem = new VirtualSystem(
            cluDevice.getAddress(),
            cipherKey
        );

        final Globals globals = new Globals();
        LoadState.install(globals);
        LuaC.install(globals);

        globals.load(new JseBaseLib());
        globals.load(new PackageLib());
        // globals.load(new Bit32Lib());
        globals.load(new TableLib());
        globals.load(new StringLib());
        // globals.load(new CoroutineLib());
        // globals.load(new JseMathLib());
        // globals.load(new JseIoLib());
        // globals.load(new JseOsLib()); // dangerous, allows OS access from LUA
        // globals.load(new LuajavaLib()); // dangerous, allows full Java access from LUA
        globals.load(new InitLuaLib(LOGGER, virtualSystem, globals));

        globals.finder = fileName -> {
            try {
                final Path filePath = aDriveDirectory.resolve(StringUtils.upperCase(fileName));
                if (!FileUtil.isParentOf(aDriveDirectory, filePath)) {
                    throw new UnexpectedException("Attempt to access external directory");
                }

                return Files.newInputStream(filePath);
            } catch (IOException e) {
                throw new UnexpectedException(e);
            }
        };

        globals.STDOUT = new PrintStream(new Slf4jLoggingOutputStream(LOGGER_STDOUT, Level.INFO));
        globals.STDERR = new PrintStream(new Slf4jLoggingOutputStream(LOGGER_STDOUT, Level.ERROR));

        return new LuaThreadWrapper(
            virtualSystem, globals,
            loadScript(aDriveDirectory, cluFile, globals)
        );
    }

    private static LuaValue executeScript(Path aDriveDirectory, CLUFiles cluFile, Globals globals) {
        return loadScript(aDriveDirectory, cluFile, globals).call();
    }

    private static LuaClosure loadScript(Path aDriveDirectory, CLUFiles cluFile, Globals globals) {
        final String scriptFileName = cluFile.getFileName();

        return new LuaClosure(
            readScript(aDriveDirectory.resolve(scriptFileName), scriptFileName, globals),
            globals
        );
    }

    private static Prototype readScript(Path path, String fileName, Globals globals) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return globals.compilePrototype(inputStream, fileName);
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }
}
