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

package pl.psobiech.opengr8on.vclu.system;

import java.io.Closeable;
import java.net.Inet4Address;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.CLUClient;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualObject;

public class ClientRegistry implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientRegistry.class);

    private final ScheduledExecutorService executor = ThreadUtil.virtualScheduler(getClass());

    private final Inet4Address localAddress;

    private final CipherKey cipherKey;

    private final Map<String, ScheduledFuture<?>> registrations = new HashMap<>();

    private final Map<String, CLUClient> clients = new HashMap<>();

    public ClientRegistry(Inet4Address localAddress, CipherKey cipherKey) {
        this.localAddress = localAddress;
        this.cipherKey    = cipherKey;
    }

    public void register(
        Inet4Address ipAddress, int port, int sessionId, Consumer<CLUClient> fn
    ) {
        final String registrationKey = createKey(ipAddress, port, sessionId);
        final CLUClient client = new CLUClient(localAddress, ipAddress, cipherKey, port);

        final Future<?> previousFuture = registrations.put(
            registrationKey,
            executor.scheduleAtFixedRate(
                () -> {
                    try {
                        fn.accept(client);
                    } catch (Exception e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                },
                1, 1, TimeUnit.SECONDS
            )
        );

        ThreadUtil.cancel(previousFuture);
        IOUtil.closeQuietly(clients.put(registrationKey, client));
    }

    public void destroy(Inet4Address ipAddress, int port, int sessionId) {
        final String registrationKey = createKey(ipAddress, port, sessionId);

        ThreadUtil.cancel(registrations.remove(registrationKey));
        IOUtil.closeQuietly(clients.remove(registrationKey));
    }

    private String createKey(Inet4Address ipAddress, int port, int sessionId) {
        return ipAddress.getHostAddress() + ":" + port + ":" + sessionId;
    }

    @Override
    public void close() {
        ThreadUtil.close(executor);

        for (CLUClient client : clients.values()) {
            IOUtil.closeQuietly(client);
        }
    }

    public record Subscription(VirtualObject object, int index) {
    }
}
