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

import java.io.Closeable;
import java.net.Inet4Address;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.CLUClient;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.exceptions.UncheckedInterruptedException;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.vclu.objects.HttpRequest;
import pl.psobiech.opengr8on.vclu.objects.MqttTopic;
import pl.psobiech.opengr8on.vclu.objects.RemoteCLU;
import pl.psobiech.opengr8on.vclu.objects.Storage;
import pl.psobiech.opengr8on.vclu.objects.Timer;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;

public class VirtualSystem implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualSystem.class);

    private static final long LOOP_TIME_NANOS = TimeUnit.MILLISECONDS.toNanos(64);

    private static final long LOG_LOOP_TIME_NANOS = TimeUnit.MILLISECONDS.toNanos(8);

    private static final long NANOS_IN_MILLISECOND = TimeUnit.MILLISECONDS.toNanos(1);

    private final ScheduledExecutorService executor = ThreadUtil.executor("LuaServer");

    private final Inet4Address localAddress;

    private final CLUDevice device;

    private final CipherKey cipherKey;

    private final Map<String, VirtualObject> objectsByName = new HashMap<>();

    private VirtualCLU currentClu = null;

    private ScheduledFuture<?> clientReportFuture = null;

    public VirtualSystem(Inet4Address localAddress, CLUDevice device, CipherKey cipherKey) {
        this.localAddress = localAddress;
        this.device       = device;
        this.cipherKey    = cipherKey;
    }

    public VirtualObject getObject(String name) {
        return objectsByName.get(name);
    }

    public VirtualCLU getCurrentClu() {
        return currentClu;
    }

    @SuppressWarnings("resource")
    public void newObject(int index, String name, Inet4Address ipAddress) {
        final VirtualObject virtualObject = switch (index) {
            // TODO: temporarily we depend that the main CLU is initialized first-ish
            case VirtualCLU.INDEX -> (currentClu = new VirtualCLU(name));
            case RemoteCLU.INDEX -> new RemoteCLU(name, ipAddress, localAddress, cipherKey);
            case Timer.INDEX -> new Timer(name);
            case Storage.INDEX -> new Storage(name);
            case MqttTopic.INDEX -> new MqttTopic(name, currentClu);
            default -> new VirtualObject(name);
        };

        objectsByName.put(name, virtualObject);
    }

    @SuppressWarnings("resource")
    public void newGate(int index, String name) {
        final VirtualObject virtualObject = switch (index) {
            case HttpRequest.INDEX -> new HttpRequest(name);
            case MqttTopic.INDEX -> new MqttTopic(name, currentClu);
            default -> new VirtualObject(name);
        };

        objectsByName.put(name, virtualObject);
    }

    public void setup() {
        for (VirtualObject value : objectsByName.values()) {
            try {
                value.setup();
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    public void loop() {
        final long startTime = System.nanoTime();
        for (VirtualObject object : objectsByName.values()) {
            final long objectStartTime = System.nanoTime();
            try {
                object.loop();
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            } finally {
                final long objectDeltaNanos = (System.nanoTime() - objectStartTime);
                if (objectDeltaNanos > LOG_LOOP_TIME_NANOS) {
                    LOGGER.warn("Object {} loop time took {}ms", object.getName(), TimeUnit.NANOSECONDS.toMillis(objectDeltaNanos));
                }
            }

            Thread.yield();
        }

        // best effort to run loop with fixed rate
        final long timeLeft = LOOP_TIME_NANOS - (System.nanoTime() - startTime);
        if (timeLeft < 0) {
            LOGGER.warn("Exceeded loop time by {}ms", -TimeUnit.NANOSECONDS.toMillis(timeLeft));
        }

        sleepNanos(Math.max(0, timeLeft));
    }

    public void sleep(long millis) {
        sleepNanos(TimeUnit.MILLISECONDS.toNanos(millis));
    }

    public void sleepNanos(long nanoSeconds) {
        final long millis = nanoSeconds / NANOS_IN_MILLISECOND;
        final int nanos = (int) (nanoSeconds % NANOS_IN_MILLISECOND);

        try {
            Thread.sleep(millis, nanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new UncheckedInterruptedException(e);
        }
    }

    public String clientRegister(Inet4Address remoteIpAddress, Inet4Address ipAddress, int port, int sessionId, List<Subscription> subscription) {
        if (clientReportFuture != null) {
            clientReportFuture.cancel(true);
        }

        clientReportFuture = executor.scheduleAtFixedRate(
            () -> {
                try {
                    final String valuesAsString = "clientReport:" + sessionId + ":" + fetchValues(subscription);

                    try (CLUClient client = new CLUClient(localAddress, ipAddress, cipherKey, port)) {
                        client.clientReport(valuesAsString);
                    }

                    if (!ipAddress.equals(remoteIpAddress)) {
                        // when having docker network interfaces,
                        // OM often picks incorrect/unreachable local address - so we send to both reported by OM and real source address
                        try (CLUClient client = new CLUClient(localAddress, remoteIpAddress, cipherKey, port)) {
                            client.clientReport(valuesAsString);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error(e.getMessage(), e);
                }
            },
            1, 1, TimeUnit.SECONDS
        );

        return "clientReport:" + sessionId + ":" + fetchValues(subscription);
    }

    public LuaValue clientDestroy(String ipAddress, int port, int sessionId) {
        if (clientReportFuture != null) {
            clientReportFuture.cancel(true);
        }

        clientReportFuture = null;

        return LuaValue.valueOf(sessionId);
    }

    public String fetchValues(List<Subscription> subscription) {
        final StringBuilder sb = new StringBuilder();
        for (Subscription entry : subscription) {
            final String name = entry.name();
            final int index = entry.index();

            if (!sb.isEmpty()) {
                sb.append(",");
            }

            sb.append(LuaUtil.toString(
                getObject(name).get(index)
            ));
        }

        return "{" + sb + "}";
    }

    @Override
    public void close() {
        ThreadUtil.close(executor);

        if (clientReportFuture != null) {
            clientReportFuture.cancel(true);

            clientReportFuture = null;
        }

        for (VirtualObject object : objectsByName.values()) {
            IOUtil.closeQuietly(object);
        }
    }

    public record Subscription(String name, int index) {
    }
}
