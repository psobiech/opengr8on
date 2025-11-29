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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.luaj.vm2.LuaValue;
import pl.psobiech.opengr8on.client.CLUFiles;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.Mocks;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.client.device.CipherTypeEnum;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.ResourceUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Server2ServerTest {
    private static final CipherKey projectCipherKey = Mocks.cipherKey();

    private MockServer mockServer0;

    private MockServer mockServer1;

    @BeforeEach
    void setUp() throws Exception {

        mockServer1 = new MockServer(
                projectCipherKey,
                new CLUDevice(
                        Mocks.serialNumber(),
                        Mocks.macAddress(),
                        MockServer.LOCALHOST,
                        CipherTypeEnum.PROJECT,
                        Mocks.iv(),
                        Mocks.pin()
                )
        );

        FileUtil.linkOrCopy(
                ResourceUtil.classPath("remote/OM2.LUA"),
                mockServer1.getADriveDirectory().resolve(CLUFiles.OM_LUA.getFileName())
        );

        mockServer1.start();

        mockServer0 = new MockServer(
                projectCipherKey,
                new CLUDevice(
                        Mocks.serialNumber(),
                        Mocks.macAddress(),
                        MockServer.LOCALHOST, mockServer1.getPort(),
                        CipherTypeEnum.PROJECT,
                        Mocks.iv(),
                        Mocks.pin()
                )
        );

        FileUtil.linkOrCopy(
                ResourceUtil.classPath("remote/OM1.LUA"),
                mockServer0.getADriveDirectory().resolve(CLUFiles.OM_LUA.getFileName())
        );

        mockServer0.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        IOUtil.closeQuietly(mockServer0, mockServer1);
    }

    @Test
    @Timeout(30)
    void remoteCommunication() throws Exception {
        final Server server0 = mockServer0.getServer();
        final Server server1 = mockServer1.getServer();

        assertEquals(LuaValue.NIL, server0.luaCall("testVariable"));
        assertEquals(333, server1.luaCall("testVariable").checkint());
        assertEquals(333, server1.luaCall("getVar(\"testVariable\")").checkint());
        assertEquals(333, server0.luaCall("CLU1:execute(0, \"testVariable\")").checkint());
        assertEquals(333, server0.luaCall("CLU1:execute(0, \"getVar(\\\"testVariable\\\")\")").checkint());

        server0.luaCall("CLU1:execute(0, \"setVar(\\\"testVariable\\\", getVar(\\\"testVariable\\\") + 1)\")");
        assertEquals(334, server1.luaCall("testVariable").checkint());

        server0.luaCall("CLU1:execute(0, \"setVar(\\\"testVariable\\\", getVar(\\\"testVariable\\\") + 1)\")");
        assertEquals(335, server1.luaCall("testVariable").checkint());

        final LuaValue resultValueRemote = server0.luaCall("CLU1:execute(0, \"getVar(\\\"testVariable\\\")\")");
        assertEquals(335, resultValueRemote.checkint());

        final LuaValue resultValueLocal = server1.luaCall("testVariable");
        assertEquals(335, resultValueLocal.checkint());
    }
}