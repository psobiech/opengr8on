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
import java.util.function.Consumer;

import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.client.device.CipherTypeEnum;
import pl.psobiech.opengr8on.util.HexUtil;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.RandomUtil;
import pl.psobiech.opengr8on.vclu.MockServer.ServerContext;

class BaseServerTest {
    void execute(ServerContext fn) throws Exception {
        execute((mockServer) -> { }, fn);
    }

    void execute(Consumer<MockServer> prepare, ServerContext fn) throws Exception {
        final long serialNumber = HexUtil.asLong(RandomUtil.hexString(8));
        final CipherKey projectCipherKey = new CipherKey(RandomUtil.bytes(16), RandomUtil.bytes(16));

        final MockServer server = new MockServer(
            projectCipherKey,
            new CLUDevice(
                serialNumber,
                RandomUtil.hexString(12),
                MockServer.LOCALHOST,
                CipherTypeEnum.PROJECT,
                RandomUtil.bytes(16),
                RandomUtil.hexString(8).getBytes(StandardCharsets.US_ASCII)
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