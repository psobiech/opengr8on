/*
 * OpenGr8on, open source extensions to systems based on Grenton devices
 * Copyright (C) 2023 Piotr Sobiech
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pl.psobiech.opengr8on.vclu;

import java.io.Closeable;
import java.net.Inet4Address;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.CLUClient;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.util.IPv4AddressUtil.NetworkInterfaceDto;
import pl.psobiech.opengr8on.util.ThreadUtil;

public class VirtualSystem implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualSystem.class);

    private final ScheduledExecutorService executors = Executors.newSingleThreadScheduledExecutor(ThreadUtil.daemonThreadFactory("LuaServer"));

    private final Path aDriveDirectory;

    private final NetworkInterfaceDto networkInterface;

    private final CLUDevice device;

    private final CipherKey cipherKey;

    private final Map<Integer, VirtualObject> objects = new HashMap<>();

    private final Map<String, VirtualObject> objectsByName = new HashMap<>();

    private ScheduledFuture<?> clientReportFuture = null;

    private int objectIdGenerator = 0;

    public VirtualSystem(Path aDriveDirectory, NetworkInterfaceDto networkInterface, CLUDevice device, CipherKey cipherKey) {
        this.aDriveDirectory = aDriveDirectory;
        this.networkInterface = networkInterface;
        this.device = device;
        this.cipherKey = cipherKey;
    }

    public VirtualObject getObject(int index) {
        return objects.get(index);
    }

    public VirtualObject getObject(String name) {
        return objectsByName.get(name);
    }

    public int newObject(int index, String name, int ipAddress) {
        final int objectId = objectIdGenerator++;

        final VirtualObject virtualObject = switch (index) {
            case 0 -> new VirtualCLU(name, IPv4AddressUtil.parseIPv4(ipAddress), aDriveDirectory);
            case 1 -> new VirtualRemoteCLU(name, IPv4AddressUtil.parseIPv4(ipAddress), networkInterface, cipherKey);
            case 44 -> new VirtualStorage(name);
            default -> new VirtualObject(name);
        };

        objects.put(objectId, virtualObject);
        objectsByName.put(name, virtualObject);

        return objectId;
    }

    public int newGate(int index, String name) {
        final int objectId = objectIdGenerator++;

        final VirtualObject virtualObject = switch (index) {
            case 121 -> new VirtualGate(name);
            default -> new VirtualObject(name);
        };

        objects.put(objectId, virtualObject);
        objectsByName.put(name, virtualObject);

        return objectId;
    }

    public void setup() {
        for (VirtualObject value : objects.values()) {
            try {
                value.setup();
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    public void loop() {
        final long startTime = System.nanoTime();
        for (VirtualObject value : objects.values()) {
            try {
                value.loop();
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        // best effort to run every second
        sleep(Math.max(
                0,
                1000 - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)
            )
        );
    }

    public void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new UnexpectedException(e);
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

                    // when having docker network interfaces,
                    // OM often picks incorrect/unreachable local address
                    try (CLUClient client = new CLUClient(networkInterface, remoteIpAddress, cipherKey, port)) {
                        client.clientReport(valuesAsString);
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

            final LuaValue luaValue = getObject(name).get(index);
            if (luaValue == null || luaValue.isnil()) {
                sb.append("nil");
            } else if (luaValue.isnumber()) {
                sb.append(luaValue.checklong());
            } else if (luaValue.isstring()) {
                sb.append("\"").append(luaValue).append("\"");
            } else {
                sb.append(luaValue);
            }
        }

        return "{" + sb + "}";
    }

    @Override
    public void close() {
        if (clientReportFuture != null) {
            clientReportFuture.cancel(true);

            clientReportFuture = null;
        }

        executors.shutdown();

        for (VirtualObject object : objects.values()) {
            FileUtil.closeQuietly(object);
        }
    }

    public record Subscription(String name, int index) {
    }
}
