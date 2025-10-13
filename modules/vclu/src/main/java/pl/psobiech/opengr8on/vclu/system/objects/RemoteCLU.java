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

package pl.psobiech.opengr8on.vclu.system.objects;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import pl.psobiech.opengr8on.exceptions.UncheckedInterruptedException;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.vclu.ServerVersion;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscoveryOrigin;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscoverySensor;
import pl.psobiech.opengr8on.vclu.system.ProjectObjectRegistry;
import pl.psobiech.opengr8on.vclu.system.VirtualSystem;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.Feature;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObjectType;

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

        register(Methods.EXECUTE, arg1 -> {
            final String script = arg1.checkjstring();

            return remoteExecute(script);
        });

        this.currentClu = virtualSystem.getCurrentClu();
    }

    @Override
    public void loop() {
        if (currentClu.isMqttEnabled() && currentClu.getMqttClient() != null && !mqttInitialized) {
            if (LuaUtil.trueish(currentClu.get(VirtualCLU.Features.MQTT_DISCOVERY))) {
                final String discoveryPrefix = currentClu.get(VirtualCLU.Features.MQTT_DISCOVERY_PREFIX).checkjstring();
                if (discoveryPrefix != null) {
                    initMqttDiscovery(discoveryPrefix);

                    mqttInitialized = true;
                }
            }
        }
    }

    private void initMqttDiscovery(String discoveryPrefix) {
        final Set<SpecificObjectType> supportedTypes = Set.of(
                SpecificObjectType.PANEL_TEMPERATURE,
                SpecificObjectType.PANEL_LUMINOSITY
//                    SpecificObjectType.POWER_SUPPLY_VOLTAGE
        );

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

            if (!supportedTypes.contains(object.getType())) {
                LOGGER.warn("Ignoring object {} on CLU {}", objectName, name);

                continue;
            }

            final String uniqueId = name + "_" + objectName;

            final Optional<Feature> valueFeature = object.getFeatures().stream()
                                                         .filter(feature1 -> feature1.getName().equalsIgnoreCase("value"))
                                                         .findAny();
            if (valueFeature.isEmpty()) {
                continue;
            }

            final Feature feature = valueFeature.get();

            final String objectClass;
            if (object.getType() == SpecificObjectType.PANEL_TEMPERATURE) {
                objectClass = "temperature";
            } else if (object.getType() == SpecificObjectType.PANEL_LUMINOSITY) {
                objectClass = null;
            } else if (object.getType() == SpecificObjectType.POWER_SUPPLY_VOLTAGE) {
                objectClass = "voltage";
            } else {
                continue;
            }

            final MqttDiscoverySensor discoveryMessage = new MqttDiscoverySensor(
                    object.getName(),
                    uniqueId,
                    "%s/%s/%s".formatted(discoveryPrefix, "sensor", uniqueId), null, "~/state",
                    objectClass,
                    feature.getUnit(),
                    "{{ value | float }}",
                    new MqttDiscoveryDevice(
                            name, clu.getName(), "Grenton",
                            clu.getSerialNumber(),
                            clu.getFirmwareType() + "_" + clu.getFirmwareVersion(),
                            clu.getHardwareType() + "_" + clu.getHardwareVersion()
                    ),
                    new MqttDiscoveryOrigin(
                            "opengr8on", ServerVersion.get(),
                            "https://github.com/psobiech/opengr8on"
                    )
            );

            try {
                currentClu.getMqttClient()
                          .publish(
                                  "%s/%s/%s".formatted(discoveryPrefix, "sensor", uniqueId) + "/config",
                                  ObjectMapperFactory.JSON.writeValueAsBytes(discoveryMessage),
                                  true
                          );
            } catch (MqttException | JsonProcessingException e) {
                LOGGER.error("Could not publish discovery message for {}", uniqueId, e);

                continue;
            }

            scheduler.execute(() -> {
                String lastState = null;
                while (!Thread.currentThread().isInterrupted()) {
                    final LuaValue luaValue = remoteExecute(object.getNameOnCLU() + ":get(" + feature.getIndex() + ")");
                    if (LuaUtil.isNil(luaValue)) {
                        continue;
                    }

                    final String state = String.valueOf(luaValue.todouble());
                    if (!state.equals(lastState)) {
                        lastState = state;

                        LOGGER.debug(uniqueId + "(" + object.getName() + "): " + state);

                        try {
                            currentClu.getMqttClient()
                                      .publish(
                                              "%s/%s/%s".formatted(discoveryPrefix, "sensor", uniqueId) + "/state",
                                              state.getBytes(StandardCharsets.UTF_8)
                                      );
                        } catch (MqttException e) {
                            LOGGER.error("Could not publish state update message for {}", uniqueId, e);

                            continue;
                        }
                    }

                    try {
                        Thread.sleep(60_000L);
                    } catch (InterruptedException e) {
                        throw new UncheckedInterruptedException(e);
                    }
                }
            });

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
