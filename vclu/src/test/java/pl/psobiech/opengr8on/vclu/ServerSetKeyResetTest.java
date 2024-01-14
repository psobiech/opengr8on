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

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import pl.psobiech.opengr8on.client.CLUClient;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.util.RandomUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.psobiech.opengr8on.vclu.MockServer.LOCALHOST;

class ServerSetKeyResetTest extends BaseServerTest {
    @Test
    void setCipherKey() throws Exception {
        execute((projectCipherKey, server, client) -> {
            final CLUDevice cluDevice = client.getCluDevice();

            final CipherKey newCipherKey = new CipherKey(RandomUtil.bytes(16), RandomUtil.bytes(16));

            final Optional<Boolean> aliveOptional = client.updateCipherKey(newCipherKey);
            assertTrue(aliveOptional.isPresent());
            assertTrue(aliveOptional.get());

            assertEquals(newCipherKey, client.getCipherKey());
            assertNotEquals(projectCipherKey, client.getCipherKey());
            try (CLUClient otherClient = new CLUClient(LOCALHOST, cluDevice, projectCipherKey, LOCALHOST, server.getPort())) {
                assertFalse(otherClient.checkAlive().isPresent());
            }

            try (CLUClient otherClient = new CLUClient(LOCALHOST, cluDevice, newCipherKey, LOCALHOST, server.getPort())) {
                final Optional<Boolean> otherAliveOptional = otherClient.updateCipherKey(newCipherKey);
                assertTrue(otherAliveOptional.isPresent());
                assertTrue(otherAliveOptional.get());
            }

            // persists after reset
            final Optional<Boolean> resetOptional = client.reset(Duration.ofMillis(8000L));
            assertTrue(resetOptional.isPresent());
            assertTrue(resetOptional.get());

            try (CLUClient otherClient = new CLUClient(LOCALHOST, cluDevice, projectCipherKey, LOCALHOST, server.getPort())) {
                assertFalse(otherClient.checkAlive().isPresent());
            }

            try (CLUClient otherClient = new CLUClient(LOCALHOST, cluDevice, newCipherKey, LOCALHOST, server.getPort())) {
                final Optional<Boolean> otherAliveOptional = otherClient.updateCipherKey(newCipherKey);
                assertTrue(otherAliveOptional.isPresent());
                assertTrue(otherAliveOptional.get());
            }
        });
    }
}