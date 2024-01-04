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
import java.nio.file.Path;
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
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil.NetworkInterfaceDto;
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

    private static final long NANOS_IN_MILLISECOND = TimeUnit.MILLISECONDS.toNanos(1);

    private final ScheduledExecutorService executors = ThreadUtil.executor("LuaServer");

    private final Path aDriveDirectory;

    private final NetworkInterfaceDto networkInterface;

    private final CLUDevice device;

    private final CipherKey cipherKey;

    private final Map<String, VirtualObject> objectsByName = new HashMap<>();

    private VirtualCLU currentClu = null;

    private ScheduledFuture<?> clientReportFuture = null;

    public VirtualSystem(Path aDriveDirectory, NetworkInterfaceDto networkInterface, CLUDevice device, CipherKey cipherKey) {
        this.aDriveDirectory = aDriveDirectory;
        this.networkInterface = networkInterface;
        this.device = device;
        this.cipherKey = cipherKey;
    }

    public VirtualObject getObject(String name) {
        return objectsByName.get(name);
    }

    public VirtualCLU getCurrentClu() {
        return currentClu;
    }

    @SuppressWarnings("resource")
    public void newObject(int index, String name, int ipAddress) {
        final VirtualObject virtualObject = switch (index) {
            // TODO: temporarily we depend that the main CLU is initialized first-ish
            case VirtualCLU.INDEX -> (currentClu = new VirtualCLU(name, IPv4AddressUtil.parseIPv4(ipAddress), aDriveDirectory));
            case RemoteCLU.INDEX -> new RemoteCLU(name, IPv4AddressUtil.parseIPv4(ipAddress), networkInterface, cipherKey);
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
        for (VirtualObject value : objectsByName.values()) {
            try {
                value.loop();
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
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

    public String clientRegister(String remoteAddress, String address, int port, int sessionId, List<Subscription> subscription) {
        if (clientReportFuture != null) {
            clientReportFuture.cancel(true);
        }

        final Inet4Address ipAddress = IPv4AddressUtil.parseIPv4(address);
        final Inet4Address remoteIpAddress = IPv4AddressUtil.parseIPv4(remoteAddress);

        clientReportFuture = executors.scheduleAtFixedRate(
            () -> {
                try {
                    final String valuesAsString = "clientReport:" + sessionId + ":" + fetchValues(subscription);

                    try (CLUClient client = new CLUClient(networkInterface, ipAddress, cipherKey, port)) {
                        client.clientReport(valuesAsString);
                    }

                    if (!ipAddress.equals(remoteIpAddress)) {
                        // when having docker network interfaces,
                        // OM often picks incorrect/unreachable local address - so we send to both reported by OM and real source address
                        try (CLUClient client = new CLUClient(networkInterface, remoteIpAddress, cipherKey, port)) {
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
        if (clientReportFuture != null) {
            clientReportFuture.cancel(true);

            clientReportFuture = null;
        }

        executors.shutdownNow();

        for (VirtualObject object : objectsByName.values()) {
            FileUtil.closeQuietly(object);
        }
    }

    public record Subscription(String name, int index) {
    }
}
