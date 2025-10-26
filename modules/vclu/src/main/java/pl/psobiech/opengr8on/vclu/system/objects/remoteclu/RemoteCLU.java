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
import pl.psobiech.opengr8on.util.ToStringUtil;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscovery;
import pl.psobiech.opengr8on.vclu.system.ProjectObjectRegistry;
import pl.psobiech.opengr8on.vclu.system.VirtualSystem;
import pl.psobiech.opengr8on.vclu.system.lua.fn.LuaOneArgFunction;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualObject;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

public class RemoteCLU extends VirtualObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCLU.class);

    public static final int INDEX = 1;

    private final ProjectObjectRegistry objectRegistry;

    private final CLUClient client;

    private final Globals localLuaContext;

    private final VirtualCLU currentClu;

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
                    scheduler.execute(() -> initMqttDiscovery(discoveryPrefix));
                }
            }
        }
    }

    private void initMqttDiscovery(String discoveryPrefix) {
        final Set<SpecificObject> specificObjects = objectRegistry.byCluName(name);
        for (SpecificObject object : specificObjects) {
            final String objectName = object.getNameOnCLU();

            SpecificObject clu = object.getClu();
            if (clu != null && clu.getReference() != null) {
                clu = objectRegistry.byReference(clu.getReference()).orElse(null);
            }

            if (clu == null) {
                LOGGER.warn("Could not find CLU for object {}", objectName);

                continue;
            }

            final RemoteCLUSensor sensor;
            switch (object.getType()) {
                case PANEL_TEMPERATURE -> sensor = new RemoteCLUTemperatureSensor(
                        discoveryPrefix, clu, object
                );
                case PANEL_LUMINOSITY -> sensor = new RemoteCLULuminositySensor(
                        discoveryPrefix, clu, object
                );
                case POWER_SUPPLY_VOLTAGE -> sensor = new RemoteCLUVoltageSensor(
                        discoveryPrefix, clu, object
                );
                case ROLLER_SHUTTER -> sensor = new RemoteCLUShutter(
                        discoveryPrefix, clu, object
                );
                case DOUT, DIMM -> sensor = new RemoteCLUDimmer(
                        discoveryPrefix, clu, object
                );
                case LED_RGB -> sensor = new RemoteCLULedRgbLight(
                        discoveryPrefix, clu, object
                );
                case UNSUPPORTED -> {
                    LOGGER.warn("Unsupported object {} on CLU {}", objectName, name);

                    continue;
                }
                case null, default -> {
                    LOGGER.warn("Ignoring object {} on CLU {}", objectName, name);

                    continue;
                }
            }

            sendDiscoveryMessage(sensor);
            subscribeCommandMessages(sensor);
            scheduleStatePolling(sensor);
        }
    }

    private void sendDiscoveryMessage(RemoteCLUSensor sensor) {
        final MqttDiscovery discoveryMessage = sensor.getDiscoveryMessage();
        final String discoveryTopic = discoveryMessage.getDiscoveryTopic();
        if (discoveryTopic == null) {
            return;
        }

        try {
            currentClu.getMqttClient()
                      .publish(
                              discoveryTopic,
                              ObjectMapperFactory.JSON.writeValueAsBytes(discoveryMessage),
                              true
                      );
        } catch (MqttException | JsonProcessingException | RuntimeException e) {
            LOGGER.error("Could not publish discovery message for {}", discoveryMessage.getUniqueId(), e);
        }
    }

    private void subscribeCommandMessages(RemoteCLUSensor sensor) {
        final MqttDiscovery discoveryMessage = sensor.getDiscoveryMessage();
        final String commandTopic = discoveryMessage.getCommandTopic();
        if (commandTopic == null) {
            return;
        }

        currentClu.getMqttClient()
                  .subscribe(
                          commandTopic,
                          bytes -> {
                              LOGGER.debug("MQTT Subscribe: {} / {}", commandTopic, ToStringUtil.toString(bytes));

                              try {
                                  Optional<JsonNode> stateNode = sensor.writeValue(this, bytes);
                                  if (stateNode.isEmpty()) {
                                      stateNode = sensor.readValue(this);
                                  }

                                  if (stateNode.isPresent()) {
                                      currentClu.getMqttClient()
                                                .publish(
                                                        discoveryMessage.getStateTopic(),
                                                        ObjectMapperFactory.JSON.writeValueAsBytes(stateNode.get())
                                                );
                                  }
                              } catch (MqttException | JsonProcessingException | RuntimeException e) {
                                  LOGGER.error("Could not publish state update message for {}", discoveryMessage.getUniqueId(), e);
                              }
                          }
                  );
    }

    private void scheduleStatePolling(RemoteCLUSensor sensor) {
        final MqttDiscovery discoveryMessage = sensor.getDiscoveryMessage();
        final String stateTopic = discoveryMessage.getStateTopic();
        if (stateTopic == null) {
            return;
        }

        scheduler.execute(() -> {
            String lastState = null;
            while (!Thread.currentThread().isInterrupted()) {
                ThreadUtil.sleepRandomized(60_000L, 45_000);

                try {
                    final Optional<JsonNode> stateNode = sensor.readValue(this);
                    if (stateNode.isEmpty()) {
                        continue;
                    }

                    final String stateAsString;
                    try {
                        stateAsString = ObjectMapperFactory.JSON.writeValueAsString(stateNode.get());
                    } catch (JsonProcessingException e) {
                        LOGGER.error("Could not serialize state for {}", discoveryMessage.getUniqueId(), e);

                        continue;
                    }

                    if (stateAsString.equals(lastState)) {
                        continue;
                    }
                    lastState = stateAsString;

                    currentClu.getMqttClient()
                              .publish(
                                      stateTopic,
                                      stateAsString.getBytes(StandardCharsets.UTF_8)
                              );
                } catch (MqttException | RuntimeException e) {
                    LOGGER.error("Could not publish state update message for {}", discoveryMessage.getUniqueId(), e);
                }
            }
        });
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
