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

import io.opentelemetry.api.trace.Tracer;
import org.luaj.vm2.LuaError;
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
import pl.psobiech.opengr8on.util.Util;
import pl.psobiech.opengr8on.vclu.Server;
import pl.psobiech.opengr8on.vclu.interfaces.DeviceInterfaces;
import pl.psobiech.opengr8on.vclu.system.ClientRegistry.Subscription;
import pl.psobiech.opengr8on.vclu.system.lua.LuaThread;
import pl.psobiech.opengr8on.vclu.system.objects.*;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU.State;
import pl.psobiech.opengr8on.vclu.system.objects.remoteclu.RemoteCLU;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;
import pl.psobiech.opengr8on.vclu.util.TraceUtil;
import pl.psobiech.opengr8on.xml.interfaces.InterfaceRegistry;

import java.io.Closeable;
import java.net.Inet4Address;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class VirtualSystem implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualSystem.class);

    private static final Tracer TRACER = TraceUtil.tracer(Server.class);

    private static final long LOG_LOOP_TIME_NANOS = TimeUnit.MILLISECONDS.toNanos(512);

    private static final long LOG_OBJECT_LOOP_TIME_NANOS = TimeUnit.MILLISECONDS.toNanos(128);

    private static final String CLIENT_REPORT_PREFIX = "clientReport:";

    private final ExecutorService executor = ThreadUtil.virtualExecutor("VSYSTEM");

    private final Inet4Address localAddress;

    private final int port;

    private final CipherKey cipherKey;

    private final Map<String, VirtualObject> objectsByName = new ConcurrentHashMap<>();

    private final Path rootDirectory;

    private final ClientRegistry clientRegistry;

    private final ProjectRegistry projectRegistry;

    private final InterfaceRegistry interfaceRegistry;

    private final VirtualCLU virtualClu;

    private LuaThread luaThread;

    public VirtualSystem(Path rootDirectory, Inet4Address localAddress, int port, CipherKey cipherKey) {
        this.rootDirectory = rootDirectory;

        this.localAddress = localAddress;
        this.port = port;
        this.cipherKey = cipherKey;

        this.clientRegistry = new ClientRegistry(localAddress, cipherKey);
        this.projectRegistry = new ProjectRegistry(rootDirectory);
        if (!projectRegistry.isEmpty()) {
            final Path parentDirectory = rootDirectory.getParent();
            final Path deviceInterfacesPath = parentDirectory.resolve("device-interfaces.zip");

            this.interfaceRegistry = DeviceInterfaces.refresh(deviceInterfacesPath);
        } else {
            this.interfaceRegistry = InterfaceRegistry.EMPTY;
        }

        this.virtualClu = new VirtualCLU(this, rootDirectory, "VIRTUAL_CLU", projectRegistry, interfaceRegistry);
    }

    public VirtualObject getObject(String name) {
        return objectsByName.get(name);
    }

    public VirtualCLU getVirtualClu() {
        return virtualClu;
    }

    @SuppressWarnings("resource")
    public void newObject(int index, String name, Inet4Address ipAddress) {
        final VirtualObject virtualObject = switch (index) {
            case VirtualCLU.INDEX -> {
                virtualClu.setName(name);

                yield virtualClu;
            }
            case RemoteCLU.INDEX -> new RemoteCLU(
                    this,
                    projectRegistry, interfaceRegistry,
                    name,
                    ipAddress, localAddress, cipherKey, port
            );
            case Timer.INDEX -> new Timer(this, name);
            case Storage.INDEX -> new Storage(this, name, rootDirectory.getParent().resolve("storage"));
            case MqttTopic.INDEX -> new MqttTopic(this, name);
            default -> new VirtualObject(this, name);
        };

        objectsByName.put(name, virtualObject);
    }

    @SuppressWarnings("resource")
    public void newGate(int index, String name) {
        final VirtualObject virtualObject = switch (index) {
            case HttpRequest.INDEX -> new HttpRequest(this, name, localAddress);
            case HttpListener.INDEX -> new HttpListener(this, name, localAddress);
            case MqttTopic.INDEX -> new MqttTopic(this, name);
            default -> new VirtualObject(this, name);
        };

        objectsByName.put(name, virtualObject);
    }

    public void setup() {
        forAllDevices(objectsByName.values(), VirtualObject::setup, "setup");

        final State state;
        if (luaThread.isEmergency()) {
            state = State.EMERGENCY;
        } else {
            state = State.OK;
        }

        virtualClu.setState(state);
        virtualClu.triggerEvent(VirtualCLU.Events.INIT);
    }

    public LuaValue luaCall(String script) throws LuaError {
        return TraceUtil.span(
                TRACER,
                () ->
                        luaThread.luaCall(script),
                getVirtualClu().getName(), "luaCall", script
        );
    }

    public void loop() {
        final long startTime = System.nanoTime();

        forAllDevices(objectsByName.values(), VirtualObject::loop, "loop");

        // best effort to run loop with fixed rate
        final long timeLeft = LOG_LOOP_TIME_NANOS - (System.nanoTime() - startTime);
        if (timeLeft < 0) {
            LOGGER.warn("Exceeded loop time by {}ms", -TimeUnit.NANOSECONDS.toMillis(timeLeft));
        }

        Util.yield();
    }

    public String clientRegister(Inet4Address remoteIpAddress, Inet4Address ipAddress, int port, int sessionId, List<Subscription> subscription) {
        clientRegistry.register(ipAddress, port, sessionId, client -> {
            final String valuesAsString = CLIENT_REPORT_PREFIX + sessionId + ":" + fetchValues(subscription);

            sendResponse(client, valuesAsString);

            if (!ipAddress.equals(remoteIpAddress)) {
                // when having docker network interfaces,
                // OM often picks incorrect/unreachable local address - so we send to both reported by OM and real source address
                try (CLUClient remoteClient = new CLUClient(localAddress, remoteIpAddress, cipherKey, port)) {
                    sendResponse(remoteClient, valuesAsString);
                }
            }
        });

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
        return LuaUtil.stringifyList(subscriptions, subscription -> {
            final VirtualObject object = subscription.object();
            final int index = subscription.index();
            final LuaValue value = object.get(index);

            return LuaUtil.stringifyRaw(value, "nil");
        });
    }

    @Override
    public void close() {
        IOUtil.closeQuietly(clientRegistry);
        IOUtil.closeQuietly(objectsByName.values());

        ThreadUtil.closeQuietly(executor);
    }

    public <VD extends VirtualDevice> void forAllDevices(Collection<VD> virtualObjects, Consumer<VD> runnable, String operationName) {
        final List<Future<?>> futures = new ArrayList<>(virtualObjects.size());
        for (VD object : virtualObjects) {
            futures.add(executor.submit(() -> {
                final long objectStartTime = System.nanoTime();
                try {
                    TraceUtil.span(
                            TRACER,
                            () ->
                                    runnable.accept(object),
                            getVirtualClu().getName(), object.getName(), operationName
                    );
                } catch (RejectedExecutionException e) {
                    LOGGER.trace(e.getMessage(), e);
                } catch (Exception e) {
                    LOGGER.error(e.getMessage(), e);
                } finally {
                    final long objectDeltaNanos = (System.nanoTime() - objectStartTime);
                    if (objectDeltaNanos > LOG_OBJECT_LOOP_TIME_NANOS) {
                        LOGGER.info("Object {} {} time took {}ms", object.getName(), operationName, TimeUnit.NANOSECONDS.toMillis(objectDeltaNanos));
                    }
                }
            }));
        }

        Util.yield();

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
