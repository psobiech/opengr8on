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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.compiler.LuaC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.CLUClient;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscovery;
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
import java.util.Optional;
import java.util.Set;

public class RemoteCLU extends VirtualObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCLU.class);

    public static final int INDEX = 1;

    private final ProjectObjectRegistry objectRegistry;

    private final CLUClient client;

    private final Globals localLuaContext;

    private final VirtualCLU currentClu;

    private final Map<String, RemoteCLUDevice> devices = new Hashtable<>();

    private boolean mqttInitialized = false;

    public RemoteCLU(VirtualSystem virtualSystem, ProjectObjectRegistry projectObjectRegistry, String name, Inet4Address address, Inet4Address localAddress, CipherKey cipherKey, int port) {
        super(
                virtualSystem, name,
                IFeature.EMPTY.class, Methods.class, IEvent.EMPTY.class,
                ThreadUtil::virtualScheduler
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

        this.currentClu = virtualSystem.getCurrentClu();
    }

    @Override
    public void loop() {
        if (currentClu.isMqttEnabled() && currentClu.getMqttClient() != null && !mqttInitialized) {
            mqttInitialized = true;

            if (LuaUtil.trueish(currentClu.get(VirtualCLU.Features.MQTT_DISCOVERY))) {
                final String discoveryPrefix = currentClu.get(VirtualCLU.Features.MQTT_DISCOVERY_PREFIX).checkjstring();
                if (discoveryPrefix != null) {
                    initMqttDiscovery(discoveryPrefix);
                }
            }
        }
    }

    private void initMqttDiscovery(String discoveryPrefix) {
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

            final RemoteCLUDevice sensor;
            switch (object.getType()) {
                case PANEL_TEMPERATURE -> sensor = new RemoteCLUTemperatureSensor(
                        scheduler, virtualSystem.getCurrentClu(), this, clu, object, discoveryPrefix
                );
                case PANEL_LUMINOSITY -> sensor = new RemoteCLULuminositySensor(
                        scheduler, virtualSystem.getCurrentClu(), this, clu, object, discoveryPrefix
                );
//                case POWER_SUPPLY_VOLTAGE -> sensor = new RemoteCLUVoltageSensor(
//                        scheduler, virtualSystem.getCurrentClu(), this, clu, object, discoveryPrefix
//                );
                case ROLLER_SHUTTER -> sensor = new RemoteCLUShutter(
                        scheduler, virtualSystem.getCurrentClu(), this, clu, object, discoveryPrefix
                );
                case DOUT, DIMM -> sensor = new RemoteCLUDimmer(
                        scheduler, virtualSystem.getCurrentClu(), this, clu, object, discoveryPrefix
                );
                case LED_RGB -> sensor = new RemoteCLULedRgbLight(
                        scheduler, virtualSystem.getCurrentClu(), this, clu, object, discoveryPrefix
                );
                case BUTTON -> sensor = new RemoteCLUButton(
                        scheduler, virtualSystem.getCurrentClu(), this, clu, object, discoveryPrefix
                );
                case PANEL_BUTTON -> sensor = new RemoteCLUButton(
                        scheduler, virtualSystem.getCurrentClu(), this, clu, object, discoveryPrefix
                );
                case UNSUPPORTED -> {
                    LOGGER.warn("Unsupported object {} on CLU {}", object.getNameOnCLU(), name);

                    continue;
                }
                case null, default -> {
                    LOGGER.warn("Ignoring object {} on CLU {}", object.getNameOnCLU(), name);

                    continue;
                }
            }

            devices.put(object.getNameOnCLU(), sensor);

            sensor.register();
        }
    }

    public void mqttOnValueChange(String nameOnCLU, LuaValue arg2) {
        final RemoteCLUDevice remoteCLUDevice = devices.get(nameOnCLU);
        if (remoteCLUDevice != null) {
            final MqttDiscovery discoveryMessage = remoteCLUDevice.getDiscoveryMessage();

            final Optional<JsonNode> stateNode = remoteCLUDevice.readValue(this);
            if (stateNode.isPresent()) {
                try {
                    currentClu.getMqttClient()
                              .publish(
                                      discoveryMessage.getStateTopic(),
                                      ObjectMapperFactory.JSON.writeValueAsBytes(stateNode.get())
                              );
                } catch (MqttException | JsonProcessingException e) {
                    LOGGER.error("Could not publish state update message for {}", discoveryMessage.getUniqueId(), e);
                }
            }
        }
    }

    public LuaValue remoteExecute(String script) {
        return client.execute(script)
                     .map(returnValue -> {
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
                     )
                     .orElse(LuaValue.NIL);
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
