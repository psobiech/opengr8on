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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import pl.psobiech.opengr8on.client.CLUClient;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.util.ThreadUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.psobiech.opengr8on.vclu.MockServer.LOCALHOST;

class ServerDiscoverCommandTest extends BaseServerTest {
    private final ExecutorService executor = ThreadUtil.daemonExecutor("ServerDiscoverCommandTest");

    @Test
    void discovery() throws Exception {
        execute((projectCipherKey, server, client) -> {
            final CLUDevice cluDevice = client.getCluDevice();

            // should not accept CLU KEY
            try (CLUClient otherClient = new CLUClient(LOCALHOST, cluDevice, cluDevice.getCipherKey(), LOCALHOST, server.getPort())) {
                final Optional<Boolean> aliveOptional = otherClient.checkAlive();

                assertFalse(aliveOptional.isPresent());
            }

            final Future<Boolean> checkAliveFuture = executor.submit(() -> {
                do {
                    final Optional<Boolean> aliveOptional = client.checkAlive();
                    if (!Thread.interrupted() && aliveOptional.isEmpty() || !aliveOptional.get()) {
                        return false;
                    }

                    Thread.sleep(100L);
                } while (!Thread.interrupted());

                return true;
            });

            final List<CLUDevice> devices;
            try (CLUClient broadcastClient = new CLUClient(LOCALHOST, cluDevice, cluDevice.getCipherKey(), LOCALHOST, server.getBroadcastPort())) {
                devices = broadcastClient.discover(
                                projectCipherKey,
                                Map.of(cluDevice.getSerialNumber(), cluDevice.getPrivateKey()),
                                Duration.ofMillis(4000L),
                                2
                        )
                        .toList();
            }

            assertEquals(1, devices.size());

            ThreadUtil.cancel(checkAliveFuture);
            try {
                assertTrue(checkAliveFuture.get());
            } catch (CancellationException e) {
                // NOP
            }

            final CLUDevice discoveredDevice = devices.getFirst();
            assertEquals(cluDevice.getName(), discoveredDevice.getName());
            assertEquals(cluDevice.getSerialNumber(), discoveredDevice.getSerialNumber());
            assertEquals(cluDevice.getAddress(), discoveredDevice.getAddress());
            assertEquals(cluDevice.getMacAddress(), discoveredDevice.getMacAddress());
            assertEquals(cluDevice.getCipherType(), discoveredDevice.getCipherType());
            assertEquals(cluDevice.getPrivateKey(), discoveredDevice.getPrivateKey());
            assertEquals(cluDevice.getCipherKey(), discoveredDevice.getCipherKey());

            // should start accepting CLU KEY
            try (CLUClient otherClient = new CLUClient(LOCALHOST, cluDevice, cluDevice.getCipherKey(), LOCALHOST, server.getPort())) {
                final Optional<Boolean> aliveOptional = otherClient.checkAlive();

                assertTrue(aliveOptional.isPresent());
                assertTrue(aliveOptional.get());
            }

            // should still accept PROJECT KEY
            try (CLUClient otherClient = new CLUClient(LOCALHOST, cluDevice, projectCipherKey, LOCALHOST, server.getPort())) {
                final Optional<Boolean> aliveOptional = otherClient.checkAlive();

                assertTrue(aliveOptional.isPresent());
                assertTrue(aliveOptional.get());
            }

            final Optional<Boolean> aliveAfterResetOptional = client.reset(Duration.ofMillis(5000L));
            assertTrue(aliveAfterResetOptional.isPresent());
            assertTrue(aliveAfterResetOptional.get());

            // should stop accepting CLU KEY after reset
            try (CLUClient otherClient = new CLUClient(LOCALHOST, cluDevice, cluDevice.getCipherKey(), LOCALHOST, server.getPort())) {
                final Optional<Boolean> aliveOptional = otherClient.checkAlive();

                assertFalse(aliveOptional.isPresent());
            }
        });
    }
}