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

import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.Mocks;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.client.device.CipherTypeEnum;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.vclu.MockServer.ServerContext;

import java.util.function.Consumer;

class BaseServerTest {
    void execute(ServerContext fn) throws Exception {
        execute((mockServer) -> {
        }, fn);
    }

    void execute(Consumer<MockServer> prepare, ServerContext fn) throws Exception {
        final long serialNumber = Mocks.serialNumber();
        final CipherKey projectCipherKey = Mocks.cipherKey();

        final MockServer server = new MockServer(
                projectCipherKey,
                new CLUDevice(
                        serialNumber,
                        Mocks.macAddress(),
                        MockServer.LOCALHOST,
                        CipherTypeEnum.PROJECT,
                        Mocks.iv(),
                        Mocks.pin()
                )
        );

        try {
            prepare.accept(server);

            server.execute(projectCipherKey, fn);
        } finally {
            IOUtil.closeQuietly(server);
        }
    }
}