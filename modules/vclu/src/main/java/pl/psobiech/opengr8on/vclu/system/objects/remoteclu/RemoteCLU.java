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

import org.apache.commons.lang3.StringUtils;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.compiler.LuaC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.CLUClient;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.Util;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.system.ProjectObjectRegistry;
import pl.psobiech.opengr8on.vclu.system.VirtualSystem;
import pl.psobiech.opengr8on.vclu.system.lua.fn.LuaOneArgFunction;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualObject;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.net.Inet4Address;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

public class RemoteCLU extends VirtualObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCLU.class);

    public static final int INDEX = 1;

    private final ProjectObjectRegistry objectRegistry;

    private final CLUClient client;

    private final Globals localLuaContext;

    private final VirtualCLU virtualClu;

    private final Map<String, RemoteCLUDevice> devices = new Hashtable<>();

    private boolean mqttInitialized = false;

    public RemoteCLU(VirtualSystem virtualSystem, ProjectObjectRegistry projectObjectRegistry, String name, Inet4Address address, Inet4Address localAddress, CipherKey cipherKey, int port) {
        super(
                virtualSystem, name,
                IFeature.EMPTY.class, Methods.class, IEvent.EMPTY.class
        );

        this.objectRegistry = projectObjectRegistry;

        this.localLuaContext = new Globals();
        // LoadState.install(globals);
        LuaC.install(localLuaContext);

        this.client = new CLUClient(localAddress, address, cipherKey, port);

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

        for (Map.Entry<String, RemoteCLUDevice> entry : devices.entrySet()) {
            final String uniqueId = entry.getKey();
            final RemoteCLUDevice remoteCLUDevice = entry.getValue();
            try {
                remoteCLUDevice.loop();
            } catch (Exception e) {
                LOGGER.error("Error while looping on remote object: {} ({})", uniqueId, e.getMessage(), e);
            }

            Util.yield();
        }
    }

    private void initMqttDiscovery(String discoveryPrefix) {
        final VirtualCLU virtualClu = virtualSystem.getVirtualClu();

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
            switch (object.getType()) {
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
//                case POWER_SUPPLY_VOLTAGE -> sensor = new RemoteCLUVoltageSensor(
//                        virtualClu, this,
//                        clu, object,
//                        discoveryPrefix,
//                        uniqueId, mqttDiscoveryDevice
//                );
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
                    LOGGER.warn("Unsupported object {} on CLU {}", object.getNameOnCLU(), name);

                    continue;
                }
                case null, default -> {
                    LOGGER.trace("Ignoring not yet supported object {} on CLU {}", object.getNameOnCLU(), name);

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

    public LuaValue remoteSet(SpecificObject object, long index, float value) {
        return remoteSet(object, index, String.valueOf(value));
    }

    public LuaValue remoteSet(SpecificObject object, long index, int value) {
        return remoteSet(object, index, String.valueOf(value));
    }

    public LuaValue remoteSet(SpecificObject object, long index, String value) {
        return remoteExecute(object, String.format("%s:set(%d, %s)", object.getNameOnCLU(), index, value));
    }

    public LuaValue remoteMethod(SpecificObject object, long index, int value) {
        return remoteMethod(object, index, String.valueOf(value));
    }

    public LuaValue remoteMethod(SpecificObject object, long index, String value) {
        return remoteExecute(object, String.format("%s:execute(%d, %s)", object.getNameOnCLU(), index, value));
    }

    public LuaValue remoteMethod(SpecificObject object, long index) {
        return remoteExecute(object, String.format("%s:execute(%d)", object.getNameOnCLU(), index));
    }

    public LuaValue remoteGet(SpecificObject object, long index) {
        return remoteExecute(object, String.format("%s:get(%d)", object.getNameOnCLU(), index));
    }

    private LuaValue remoteExecute(SpecificObject object, String script) {
        return Util.timed(
                LOGGER, "(%s) %s".formatted(object.getName(), script), 64,
                () ->
                        client.execute(script)
                              .map(this::asLuaValue)
                              .orElse(LuaValue.NIL)
        );
    }

    private LuaValue remoteExecute(String script) {
        return Util.timed(
                LOGGER, script, 64,
                () ->
                        client.execute(script)
                              .map(this::asLuaValue)
                              .orElse(LuaValue.NIL)
        );
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

        IOUtil.closeQuietly(client);
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
