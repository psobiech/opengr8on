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

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.luaj.vm2.LuaValue;
import pl.psobiech.opengr8on.client.CLUFiles;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.client.device.CipherTypeEnum;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.HexUtil;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.RandomUtil;
import pl.psobiech.opengr8on.util.ResourceUtil;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Server2ServerTest {
    private static CipherKey projectCipherKey;

    private static MockServer server1;

    private static MockServer server2;

    @BeforeAll
    static void setUp() throws Exception {
        projectCipherKey = new CipherKey(RandomUtil.bytes(16), RandomUtil.bytes(16));

        final long serialNumber2 = HexUtil.asLong(RandomUtil.hexString(8));
        server2 = new MockServer(
            projectCipherKey,
            new CLUDevice(
                serialNumber2,
                RandomUtil.hexString(12),
                MockServer.LOCALHOST,
                CipherTypeEnum.PROJECT,
                RandomUtil.bytes(16),
                RandomUtil.hexString(8).getBytes(StandardCharsets.US_ASCII)
            )
        );

        FileUtil.linkOrCopy(
            ResourceUtil.classPath("remote/OM2.LUA"),
            server2.getADriveDirectory().resolve(CLUFiles.OM_LUA.getFileName())
        );

        server2.start();

        final long serialNumber1 = HexUtil.asLong(RandomUtil.hexString(8));
        server1 = new MockServer(
            projectCipherKey,
            new CLUDevice(
                serialNumber1,
                RandomUtil.hexString(12),
                MockServer.LOCALHOST, server2.getPort(),
                CipherTypeEnum.PROJECT,
                RandomUtil.bytes(16),
                RandomUtil.hexString(8).getBytes(StandardCharsets.US_ASCII)
            )
        );

        FileUtil.linkOrCopy(
            ResourceUtil.classPath("remote/OM1.LUA"),
            server1.getADriveDirectory().resolve(CLUFiles.OM_LUA.getFileName())
        );

        server1.start();
    }

    @AfterAll
    static void tearDown() throws Exception {
        IOUtil.closeQuietly(server1);
        IOUtil.closeQuietly(server2);
    }

    @Test
    @Timeout(10)
    void remoteCommunication() throws Exception {
        assertEquals(LuaValue.NIL, server1.getServer().luaCall("testVariable"));

        final LuaValue initialValue2 = server2.getServer().luaCall("testVariable");
        assertEquals(333, LuaUtil.asObject(initialValue2));

        server1.getServer().luaCall("CLU1:execute(0, \"setVar(\\\"testVariable\\\", getVar(\\\"testVariable\\\")+ 1)\")");

        LuaValue server2value;
        do {
            server2value = server2.getServer().luaCall("testVariable");
        } while (server2value.checkint() != 334);

        final LuaValue resultValueRemote = server1.getServer().luaCall("CLU1:execute(0, \"getVar(\\\"testVariable\\\")\")");
        assertEquals("334", LuaUtil.asObject(resultValueRemote));

        final LuaValue resultValueLocal = server2.getServer().luaCall("testVariable");
        assertEquals(334, LuaUtil.asObject(resultValueLocal));
    }
}