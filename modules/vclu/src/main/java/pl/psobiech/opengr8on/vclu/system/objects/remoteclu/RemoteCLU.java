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

package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import io.opentelemetry.api.trace.Tracer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.luajc.LuaJC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.CLUClient;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.ToStringUtil;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.system.ProjectObjectRegistry;
import pl.psobiech.opengr8on.vclu.system.VirtualSystem;
import pl.psobiech.opengr8on.vclu.system.lua.fn.LuaOneArgFunction;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualObject;
import pl.psobiech.opengr8on.vclu.system.objects.remoteclu.devices.*;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;
import pl.psobiech.opengr8on.vclu.util.TraceUtil;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObjectType;

import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class RemoteCLU extends VirtualObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCLU.class);

    private static final Tracer TRACER = TraceUtil.tracer(RemoteCLU.class);

    public static final int INDEX = 1;

    private static final Set<SpecificObjectType> ENABLED_OBJECT_TYPES = Set.of(
            SpecificObjectType.PANEL_TEMPERATURE,
            SpecificObjectType.PANEL_LUMINOSITY,
            SpecificObjectType.POWER_SUPPLY_VOLTAGE,
            SpecificObjectType.ROLLER_SHUTTER,
            SpecificObjectType.DOUT,
            SpecificObjectType.DIMM,
            SpecificObjectType.LED_RGB,
            SpecificObjectType.BUTTON,
            SpecificObjectType.PANEL_BUTTON
    );

    private final ProjectObjectRegistry objectRegistry;

//    private final CLUClient client;

    private final Globals localLuaContext;

    private final VirtualCLU virtualClu;

    private final GenericObjectPool<CLUClient> clientPool;

    private final Map<String, RemoteCLUDevice> devices = new ConcurrentHashMap<>();

    private boolean mqttInitialized = false;

    public RemoteCLU(VirtualSystem virtualSystem, ProjectObjectRegistry projectObjectRegistry, String name, Inet4Address address, Inet4Address localAddress, CipherKey cipherKey, int port) {
        super(
                virtualSystem, name,
                IFeature.EMPTY.class, Methods.class, IEvent.EMPTY.class
        );

        this.objectRegistry = projectObjectRegistry;

        this.localLuaContext = new Globals();
        localLuaContext.compiler = LuaC.instance;
        localLuaContext.loader = LuaJC.instance;
        localLuaContext.undumper = LoadState.instance;

        final GenericObjectPoolConfig<CLUClient> clientPoolConfiguration = new GenericObjectPoolConfig<>();
        clientPoolConfiguration.setMinIdle(1);
        clientPoolConfiguration.setMaxTotal(4);
        clientPoolConfiguration.setBlockWhenExhausted(true);

        this.clientPool = new GenericObjectPool<>(
                new BasePooledObjectFactory<>() {
                    @Override
                    public CLUClient create() throws Exception {
                        return new CLUClient(localAddress, address, cipherKey, port);
                    }

                    @Override
                    public PooledObject<CLUClient> wrap(CLUClient cluClient) {
                        return new DefaultPooledObject<>(cluClient);
                    }

                    @Override
                    public void destroyObject(PooledObject<CLUClient> pooledObject) {
                        IOUtil.closeQuietly(pooledObject.getObject());
                    }
                },
                clientPoolConfiguration
        );

        try {
            clientPool.preparePool();
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }

        register(Methods.EXECUTE, (LuaOneArgFunction) arg1 -> {
            final String script = arg1.checkjstring();

            return remoteExecute(script);
        });

        this.virtualClu = virtualSystem.getVirtualClu();
    }

    @Override
    public void loop() {
        final boolean mqttConnected = LuaUtil.trueish(virtualClu.get(VirtualCLU.Features.MQTT_CONNECTION));
        if (virtualClu.isMqttEnabled() && virtualClu.getMqttClient() != null && mqttConnected) {
            final boolean mqttDiscoveryEnabled = LuaUtil.trueish(virtualClu.get(VirtualCLU.Features.MQTT_DISCOVERY));
            if (mqttDiscoveryEnabled && !mqttInitialized) {
                final String discoveryPrefix = virtualClu.get(VirtualCLU.Features.MQTT_DISCOVERY_PREFIX).checkjstring();
                if (discoveryPrefix != null) {
                    initMqttDiscovery(discoveryPrefix);

                    mqttInitialized = true;
                }
            }
        }

        virtualSystem.forAllDevices(
                devices.values(),
                remoteCLUDevice -> {
                    try {
                        remoteCLUDevice.loop();
                    } catch (Exception e) {
                        LOGGER.error("Error while looping on remote object: {} ({})", remoteCLUDevice.getName(), e.getMessage(), e);
                    }
                },
                "loop"
        );
    }

    private void initMqttDiscovery(String discoveryPrefix) {
        final VirtualCLU virtualClu = virtualSystem.getVirtualClu();

        virtualClu.getMqttClient()
                  .subscribe(discoveryPrefix + "/status", bytes -> {
                      LOGGER.info("DISCOVERY RESTART: {}", ToStringUtil.toString(bytes));

                      final String stateAsString = new String(bytes, StandardCharsets.UTF_8);
                      if (!stateAsString.equals("online")) {
                          return;
                      }

                      for (RemoteCLUDevice remoteCLUDevice : devices.values()) {
                          remoteCLUDevice.refreshContext()
                                         .ifPresent(refreshContext ->
                                                            refreshContext.scheduleNextRefreshRandomized(1)
                                         );
                      }
                  });

        final Set<SpecificObject> specificObjects = objectRegistry.byCluName(name);
        for (SpecificObject object : specificObjects) {
            if (Boolean.TRUE.equals(object.getRemoved())) {
                continue;
            }

            if (Boolean.FALSE.equals(object.getVisible())) {
                continue;
            }

            SpecificObject clu = object.getClu();
            if (clu != null && clu.getReference() != null) {
                clu = objectRegistry.byReference(clu.getReference()).orElse(null);
            }

            if (clu == null) {
                LOGGER.warn("Could not find CLU for object {}", object.getNameOnCLU());

                continue;
            }

            final String uniqueId = clu.getNameOnCLU() + "_" + object.getNameOnCLU();
            final MqttDiscoveryDevice mqttDiscoveryDevice = new MqttDiscoveryDevice(clu);

            final RemoteCLUDevice sensor;
            final SpecificObjectType objectType = object.getType();
            if (!ENABLED_OBJECT_TYPES.contains(objectType)) {
                LOGGER.info("Ignoring object {} of type {} on {}", object.getNameOnCLU(), objectType, name);

                continue;
            }

            switch (objectType) {
                case PANEL_TEMPERATURE -> sensor = new RemoteCLUTemperatureSensor(
                        virtualClu, this,
                        clu, object,
                        discoveryPrefix,
                        uniqueId, mqttDiscoveryDevice
                );
                case PANEL_LUMINOSITY -> sensor = new RemoteCLULuminositySensor(
                        virtualClu, this,
                        clu, object,
                        discoveryPrefix,
                        uniqueId, mqttDiscoveryDevice
                );
                case POWER_SUPPLY_VOLTAGE -> sensor = new RemoteCLUVoltageSensor(
                        virtualClu, this,
                        clu, object,
                        discoveryPrefix,
                        uniqueId, mqttDiscoveryDevice
                );
                case ROLLER_SHUTTER -> sensor = new RemoteCLUShutter(
                        virtualClu, this,
                        clu, object,
                        discoveryPrefix,
                        uniqueId, mqttDiscoveryDevice
                );
                case DOUT -> sensor = new RemoteCLULight(
                        virtualClu, this,
                        clu, object,
                        discoveryPrefix,
                        uniqueId, mqttDiscoveryDevice
                );
                case DIMM -> sensor = new RemoteCLUDimmer(
                        virtualClu, this,
                        clu, object,
                        discoveryPrefix,
                        uniqueId, mqttDiscoveryDevice
                );
                case LED_RGB -> sensor = new RemoteCLULedRgbLight(
                        virtualClu, this,
                        clu, object,
                        discoveryPrefix,
                        uniqueId, mqttDiscoveryDevice
                );
                case BUTTON, PANEL_BUTTON -> sensor = new RemoteCLUButton(
                        virtualClu, this,
                        clu, object,
                        discoveryPrefix,
                        uniqueId, mqttDiscoveryDevice
                );
                case UNSUPPORTED -> {
                    LOGGER.warn("Unsupported object {} on {}", object.getNameOnCLU(), name);

                    continue;
                }
                case null, default -> {
                    LOGGER.trace("Ignoring not yet supported object {} on {}", object.getNameOnCLU(), name);

                    continue;
                }
            }

            devices.put(object.getNameOnCLU(), sensor);

            sensor.setup();
        }
    }

    public void mqttOnValueChange(String nameOnCLU, LuaValue arg2) {
        final RemoteCLUDevice remoteCLUDevice = devices.get(nameOnCLU);
        if (remoteCLUDevice != null) {
            LOGGER.trace("Received event: mqttOnValueChange(\"{}->{}\")", name, nameOnCLU);

            remoteCLUDevice.scheduleRefreshNow();
        } else {
            LOGGER.warn("Unhandled mqttOnValueChange(\"{}->{}\")", name, nameOnCLU);
        }
    }

    public LuaValue remoteSet(SpecificObject object, long index, float param1) {
        return remoteExecute(object, String.format("%s:set(%d, %f)", object.getNameOnCLU(), index, param1));
    }

    public LuaValue remoteSet(SpecificObject object, long index, int param1) {
        return remoteExecute(object, String.format("%s:set(%d, %d)", object.getNameOnCLU(), index, param1));
    }

    public LuaValue remoteSet(SpecificObject object, long index, String param1) {
        return remoteExecute(object, String.format("%s:set(%d, '%s')", object.getNameOnCLU(), index, param1));
    }

    public LuaValue remoteMethod(SpecificObject object, long index, int param1) {
        return remoteExecute(object, String.format("%s:execute(%d, %d)", object.getNameOnCLU(), index, param1));
    }

    public LuaValue remoteMethod(SpecificObject object, long index, int param1, int param2) {
        return remoteExecute(object, String.format("%s:execute(%d, %d, %d)", object.getNameOnCLU(), index, param1, param2));
    }

    public LuaValue remoteMethod(SpecificObject object, long index, String param1, int param2) {
        return remoteExecute(object, String.format("%s:execute(%d, '%s', %d)", object.getNameOnCLU(), index, param1, param2));
    }

    public LuaValue remoteMethod(SpecificObject object, long index, String param1) {
        return remoteExecute(object, String.format("%s:execute(%d, '%s')", object.getNameOnCLU(), index, param1));
    }

    public LuaValue remoteMethod(SpecificObject object, long index, String param1, String param2) {
        return remoteExecute(object, String.format("%s:execute(%d, '%s', '%s')", object.getNameOnCLU(), index, param1, param2));
    }

    public LuaValue remoteMethod(SpecificObject object, long index) {
        return remoteExecute(object, String.format("%s:execute(%d)", object.getNameOnCLU(), index));
    }

    public LuaValue remoteGet(SpecificObject object, long index) {
        return remoteExecute(object, String.format("%s:get(%d)", object.getNameOnCLU(), index));
    }

    private LuaValue remoteExecute(SpecificObject object, String script) {
        return borrowClient(
                client ->
                        TraceUtil.span(
                                TRACER,
                                () ->
                                        client.execute(script)
                                              .map(this::asLuaValue)
                                              .orElse(LuaValue.NIL),
                                virtualClu.getName(), getName(), object.getName(), "remoteExecute", script
                        )
        );
    }

    private LuaValue remoteExecute(String script) {
        return borrowClient(
                client ->
                        TraceUtil.span(
                                TRACER,
                                () ->
                                        client.execute(script)
                                              .map(this::asLuaValue)
                                              .orElse(LuaValue.NIL),
                                virtualClu.getName(), getName(), "remoteExecute", script
                        )
        );
    }

    private <T> T borrowClient(Function<CLUClient, T> function) {
        try {
            final CLUClient client = clientPool.borrowObject();

            try {
                return function.apply(client);
            } finally {
                clientPool.returnObject(client);
            }
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }

    private LuaValue asLuaValue(String returnValue) {
        returnValue = StringUtils.stripToNull(returnValue);
        if (returnValue == null) {
            return null;
        }

        if (returnValue.startsWith("{")) {
            try {
                return localLuaContext.load("return %s".formatted(returnValue))
                                      .call();
            } catch (Exception e) {
                // Might not have been a proper LUA table
                // TODO: implement a more robust check

                LOGGER.error(e.getMessage(), e);
            }
        }

        final LuaString luaString = LuaValue.valueOf(returnValue);
        if (luaString.isnumber()) {
            return luaString.checknumber();
        }

        return luaString;
    }

    @Override
    public void close() {
        super.close();

        IOUtil.closeQuietly(clientPool);
    }

    private enum Methods implements IMethod {
        EXECUTE(0),
        //
        ;

        private final int index;

        Methods(int index) {
            this.index = index;
        }

        @Override
        public int index() {
            return index;
        }
    }
}
