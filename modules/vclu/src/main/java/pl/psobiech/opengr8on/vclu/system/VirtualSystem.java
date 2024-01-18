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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.CLUClient;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.commands.LuaScriptCommand;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.exceptions.UncheckedInterruptedException;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.RandomUtil;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.vclu.system.ClientRegistry.Subscription;
import pl.psobiech.opengr8on.vclu.system.lua.LuaThread;
import pl.psobiech.opengr8on.vclu.system.objects.HttpRequest;
import pl.psobiech.opengr8on.vclu.system.objects.MqttTopic;
import pl.psobiech.opengr8on.vclu.system.objects.RemoteCLU;
import pl.psobiech.opengr8on.vclu.system.objects.Storage;
import pl.psobiech.opengr8on.vclu.system.objects.Timer;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU.Features;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU.State;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualObject;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;

public class VirtualSystem implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualSystem.class);

    private static final long LOOP_TIME_NANOS = TimeUnit.MILLISECONDS.toNanos(64);

    private static final long LOG_LOOP_TIME_NANOS = TimeUnit.MILLISECONDS.toNanos(8);

    private static final long NANOS_IN_MILLISECOND = TimeUnit.MILLISECONDS.toNanos(1);

    private static final String CLIENT_REPORT_PREFIX = "clientReport:";

    private final ExecutorService executor = ThreadUtil.virtualExecutor("VSYSTEM");

    private final Inet4Address localAddress;

    private final int port;

    private final CipherKey cipherKey;

    private final Map<String, VirtualObject> objectsByName = new HashMap<>();

    private final Path rootDirectory;

    private final ClientRegistry clientRegistry;

    private LuaThread luaThread;

    private VirtualCLU currentClu = null;

    public VirtualSystem(Path rootDirectory, Inet4Address localAddress, int port, CipherKey cipherKey) {
        this.rootDirectory = rootDirectory;
        this.localAddress  = localAddress;
        this.port          = port;
        this.cipherKey     = cipherKey;

        this.clientRegistry = new ClientRegistry(localAddress, cipherKey);
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
            case VirtualCLU.INDEX -> (currentClu = new VirtualCLU(name));
            case RemoteCLU.INDEX -> new RemoteCLU(name, ipAddress, localAddress, cipherKey, port);
            case Timer.INDEX -> new Timer(name);
            case Storage.INDEX -> new Storage(name, luaThread, rootDirectory.getParent().resolve("storage"));
            case MqttTopic.INDEX -> new MqttTopic(name, this);
            default -> new VirtualObject(name);
        };

        objectsByName.put(name, virtualObject);
    }

    @SuppressWarnings("resource")
    public void newGate(int index, String name) {
        final VirtualObject virtualObject = switch (index) {
            case HttpRequest.INDEX -> new HttpRequest(name, localAddress);
            case MqttTopic.INDEX -> new MqttTopic(name, this);
            default -> new VirtualObject(name);
        };

        objectsByName.put(name, virtualObject);
    }

    public void setup() {
        forAllObjects(VirtualObject::setup);

        if (currentClu != null) {
            final State state;
            if (luaThread.isEmergency()) {
                state = State.EMERGENCY;
            } else {
                state = State.OK;
            }

            currentClu.set(Features.STATE, LuaValue.valueOf(state.value()));
            currentClu.triggerEvent(VirtualCLU.Events.INIT);
        }
    }

    public void loop() {
        final long startTime = System.nanoTime();

        forAllObjects(VirtualObject::loop);

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
            throw new UncheckedInterruptedException(e);
        }
    }

    public String clientRegister(
        Inet4Address remoteIpAddress, Inet4Address ipAddress, int port, int sessionId,
        List<Subscription> subscription
    ) {
        clientRegistry.register(
            ipAddress, port, sessionId,
            client -> {
                final String valuesAsString = CLIENT_REPORT_PREFIX + sessionId + ":" + fetchValues(subscription);

                sendResponse(client, valuesAsString);

                if (!ipAddress.equals(remoteIpAddress)) {
                    // when having docker network interfaces,
                    // OM often picks incorrect/unreachable local address - so we send to both reported by OM and real source address
                    try (CLUClient remoteClient = new CLUClient(localAddress, remoteIpAddress, cipherKey, port)) {
                        sendResponse(remoteClient, valuesAsString);
                    }
                }
            }
        );

        return CLIENT_REPORT_PREFIX + sessionId + ":" + fetchValues(subscription);
    }

    /**
     * Sends the execute LUA response to the given CLU
     */
    public void sendResponse(CLUClient client, String value) {
        final CLUDevice cluDevice = client.getCluDevice();

        final int sessionId = RandomUtil.integer();
        final LuaScriptCommand.Response response = LuaScriptCommand.response(cluDevice.getAddress(), sessionId, value);

        client.send(response);
    }

    public LuaValue clientDestroy(Inet4Address ipAddress, int port, int sessionId) {
        clientRegistry.destroy(ipAddress, port, sessionId);

        return LuaValue.valueOf(sessionId);
    }

    @SuppressWarnings("resource")
    public String fetchValues(List<Subscription> subscriptions) {
        return LuaUtil.stringifyList(
            subscriptions,
            subscription -> {
                final VirtualObject object = subscription.object();
                final int index = subscription.index();
                final LuaValue value = object.get(index);

                return LuaUtil.stringify(value);
            }
        );
    }

    @Override
    public void close() {
        forAllObjects(IOUtil::closeQuietly);

        ThreadUtil.closeQuietly(executor);

        IOUtil.closeQuietly(clientRegistry);
    }

    public void forAllObjects(Consumer<VirtualObject> runnable) {
        final ArrayList<Future<?>> futures = new ArrayList<>(objectsByName.size());

        for (VirtualObject object : objectsByName.values()) {
            futures.add(
                executor.submit(() -> {
                    final long objectStartTime = System.nanoTime();
                    try {
                        runnable.accept(object);
                    } catch (Exception e) {
                        LOGGER.error(e.getMessage(), e);
                    } finally {
                        final long objectDeltaNanos = (System.nanoTime() - objectStartTime);
                        if (objectDeltaNanos > LOG_LOOP_TIME_NANOS) {
                            LOGGER.warn("Object {} loop time took {}ms", object.getName(), TimeUnit.NANOSECONDS.toMillis(objectDeltaNanos));
                        }
                    }
                })
            );
        }

        Thread.yield();

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                throw new UncheckedInterruptedException(e);
            } catch (ExecutionException e) {
                final Throwable cause = e.getCause();

                LOGGER.error(cause.getMessage(), cause);
            }
        }
    }

    public void setLuaThread(LuaThread luaThread) {
        this.luaThread = luaThread;
    }
}
