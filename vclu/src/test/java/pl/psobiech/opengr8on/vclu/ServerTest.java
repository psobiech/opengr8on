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

package pl.psobiech.opengr8on.vclu;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import pl.psobiech.opengr8on.client.CLUFiles;
import pl.psobiech.opengr8on.client.commands.LuaScriptCommand;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.ResourceUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerTest extends BaseServerTest {
    @Test
    @Timeout(30)
    void normalMode() throws Exception {
        execute((projectCipherKey, server, client) -> {
            final Optional<Boolean> aliveOptional = client.checkAlive();

            assertTrue(aliveOptional.isPresent());
            assertTrue(aliveOptional.get());
        });
    }

    @Test
    @Timeout(30)
    void emergencyMode() throws Exception {
        execute(
            server -> {
                try {
                    Files.delete(server.getADriveDirectory().resolve(CLUFiles.MAIN_LUA.getFileName()));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            },
            (projectCipherKey, server, client) -> {
                final Optional<String> aliveOptional = client.execute(LuaScriptCommand.CHECK_ALIVE);

                assertTrue(aliveOptional.isPresent());
                assertEquals("emergency", aliveOptional.get());
            }
        );
    }

    @Test
    @Timeout(30)
    void fullMode() throws Exception {
        execute(
            server -> {
                FileUtil.linkOrCopy(
                    ResourceUtil.classPath("full/" + CLUFiles.USER_LUA.getFileName()),
                    server.getADriveDirectory().resolve(CLUFiles.USER_LUA.getFileName())
                );
                FileUtil.linkOrCopy(
                    ResourceUtil.classPath("full/" + CLUFiles.OM_LUA.getFileName()),
                    server.getADriveDirectory().resolve(CLUFiles.OM_LUA.getFileName())
                );
            },
            (projectCipherKey, server, client) -> {
                Thread.sleep(2000L);

                final Optional<Boolean> aliveOptional = client.checkAlive();

                assertTrue(aliveOptional.isPresent());
                assertTrue(aliveOptional.get());
            }
        );
    }
}