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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.luaj.vm2.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.vclu.MqttClient;
import pl.psobiech.opengr8on.vclu.ServerVersion;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscoveryOrigin;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscovery;
import pl.psobiech.opengr8on.vclu.system.ProjectObjectRegistry;
import pl.psobiech.opengr8on.vclu.system.VirtualSystem;
import pl.psobiech.opengr8on.vclu.system.lua.fn.LuaTwoArgFunction;
import pl.psobiech.opengr8on.vclu.system.lua.fn.LuaVarArgFunction;
import pl.psobiech.opengr8on.vclu.system.objects.clu.CLUTimeZone;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.io.Closeable;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class VirtualCLU extends VirtualObject implements Closeable {
    public static final int INDEX = 0;

    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualCLU.class);

    private static final int TIME_CHANGE_EVENT_TRIGGER_DELTA_SECONDS = 60;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

    private final List<MqttTopic> mqttTopics = new LinkedList<>();

    private volatile ZonedDateTime currentDateTime = getCurrentDateTime();

    private final ProjectObjectRegistry objectRegistry;

    private MqttClient mqttClient;

    public VirtualCLU(VirtualSystem virtualSystem, String name, ProjectObjectRegistry objectRegistry) {
        super(
                virtualSystem, name,
                Features.class, Methods.class, Events.class
        );

        this.objectRegistry = objectRegistry;

        register(Features.UPTIME, this::getUptime);
        set(Features.STATE, LuaValue.valueOf(State.STARTING.value));
        register(Features.DATE, this::getCurrentDateAsString);
        register(Features.TIME, this::getCurrentTimeAsString);
        register(Features.DAY_OF_MONTH, this::getCurrentDayOfMonth);
        register(Features.MONTH, this::getCurrentMonth);
        register(Features.YEAR, this::getCurrentYear);
        register(Features.DAY_OF_WEEK, this::getCurrentDayOfWeek);
        register(Features.HOUR, this::getCurrentHour);
        register(Features.MINUTE, this::getCurrentMinute);
        register(Features.TIMESTAMP, this::getCurrentEpochSeconds);
        set(Features.TIME_ZONE, LuaValue.valueOf(CLUTimeZone.UTC.value()));

        set(Features.MQTT_URL, LuaValue.valueOf("ssl://localhost:8883"));
        set(Features.USE_MQTT, LuaValue.valueOf(false));
        set(Features.MQTT_CONNECTION, LuaValue.valueOf(false));

        set(Features.MQTT_DISCOVERY, LuaValue.valueOf(false));
        set(Features.MQTT_DISCOVERY_PREFIX, LuaValue.valueOf("homeassistant"));
        set(Features.MQTT_MESSAGE, LuaValue.tableOf());

        register(Methods.ADD_TO_LOG, this::addToLog);
        register(Methods.CLEAR_LOG, this::clearLog);

        register(Methods.MQTT_SEND_DISCOVERY, (LuaVarArgFunction) this::mqttSendDiscovery);
        register(Methods.MQTT_SEND_VALUE, (LuaTwoArgFunction) this::mqttSendValue);

        addEventHandler(Events.MQTT_RECEIVE_VALUE, () -> {
            final LuaValue luaValue = get(Features.MQTT_MESSAGE);
        });

        scheduler.scheduleAtFixedRate(() -> {
                                          final ZonedDateTime lastDateTime = currentDateTime;
                                          currentDateTime = getCurrentDateTime();

                                          if (!currentDateTime.getZone().equals(lastDateTime.getZone())
                                                  || Duration.between(lastDateTime, currentDateTime).abs().getSeconds() >= TIME_CHANGE_EVENT_TRIGGER_DELTA_SECONDS) {
                                              triggerEvent(Events.TIME_CHANGE);
                                          }
                                      },
                                      1, 1, TimeUnit.SECONDS
        );
    }

    public boolean isMqttEnabled() {
        return LuaUtil.trueish(
                get(Features.USE_MQTT)
        );
    }

    public String getMqttUrl() {
        return LuaUtil.stringifyRaw(get(Features.MQTT_URL));
    }

    public void setMqttConnected(boolean value) {
        set(Features.MQTT_CONNECTION, LuaValue.valueOf(value));
    }

    private LuaNumber getUptime() {
        return LuaValue.valueOf(
                TimeUnit.MILLISECONDS.toSeconds(
                        runtimeBean.getUptime()
                )
        );
    }

    private LuaString getCurrentDateAsString() {
        return LuaValue.valueOf(
                currentDateTime
                        .format(DATE_FORMATTER)
        );
    }

    private LuaString getCurrentTimeAsString() {
        return LuaValue.valueOf(
                currentDateTime
                        .format(TIME_FORMATTER)
        );
    }

    private LuaInteger getCurrentDayOfMonth() {
        return LuaValue.valueOf(
                currentDateTime
                        .getDayOfMonth()
        );
    }

    private LuaInteger getCurrentMonth() {
        return LuaValue.valueOf(
                currentDateTime
                        .getMonthValue()
        );
    }

    private LuaInteger getCurrentYear() {
        return LuaValue.valueOf(
                currentDateTime
                        .getYear()
        );
    }

    private LuaInteger getCurrentDayOfWeek() {
        return LuaValue.valueOf(
                currentDateTime
                        .getDayOfWeek()
                        .getValue()
        );
    }

    private LuaInteger getCurrentHour() {
        return LuaValue.valueOf(
                currentDateTime
                        .getHour()
        );
    }

    private LuaInteger getCurrentMinute() {
        return LuaValue.valueOf(
                currentDateTime
                        .getMinute()
        );
    }

    private ZonedDateTime getCurrentDateTime() {
        final ZoneId zoneId = getCurrentZoneId();

        return ZonedDateTime.now()
                            .withZoneSameInstant(zoneId);
    }

    private ZoneId getCurrentZoneId() {
        final LuaValue zoneIdLuaValue = get(Features.TIME_ZONE);
        if (!zoneIdLuaValue.isint()) {
            return ZoneOffset.UTC;
        }

        return CLUTimeZone.valueOf(zoneIdLuaValue.checkint())
                          .zoneId();
    }

    private LuaNumber getCurrentEpochSeconds() {
        return LuaValue.valueOf(
                currentDateTime.toInstant()
                               .getEpochSecond()
        );
    }

    private LuaValue addToLog(LuaValue arg1) {
        set(Features.LOG, arg1);

        if (!arg1.isnil()) {
            LOGGER.info(name + ": " + arg1.checkjstring());
        }

        return LuaValue.NIL;
    }

    private LuaValue clearLog() {
        set(Features.LOG, LuaValue.NIL);

        return LuaValue.NIL;
    }

    private LuaValue mqttSendDiscovery(Varargs args) {
        final String name = args.checkjstring(1);
        final String uniqueId = args.checkjstring(2);
        final String deviceClass = args.optjstring(3, null);
        final String unit = args.optjstring(4, null);
        final String valueTemplate = args.optjstring(5, null);

        final String cluName = getName();
        final SpecificObject clu = objectRegistry.cluByName(cluName);

        final String discoveryPrefix = get(Features.MQTT_DISCOVERY_PREFIX).checkjstring();
        final String rootTopic = "%s/%s/%s".formatted(discoveryPrefix, "sensor", uniqueId);

        final MqttDiscovery discoveryMessage = new MqttDiscovery(
                name,
                uniqueId,
                rootTopic,
                "~/set", "~/state",
                deviceClass,
                unit,
                null,
                valueTemplate,
                new MqttDiscoveryDevice(
                        clu.getNameOnCLU(), clu.getName(), "Grenton",
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
            mqttClient
                    .publish(
                            rootTopic + "/config",
                            ObjectMapperFactory.JSON.writeValueAsBytes(discoveryMessage),
                            true
                    );
        } catch (MqttException | JsonProcessingException e) {
            throw new UnexpectedException("Could not publish discovery message for " + discoveryMessage.getUniqueId(), e);
        }

        mqttClient.subscribe(
                rootTopic + "/set",
                bytes -> {
                    try {
                        final JsonNode jsonNode = ObjectMapperFactory.JSON.readTree(bytes);
                        final int redValue = jsonNode.get("r").asInt(0);
                        final int greenValue = jsonNode.get("g").asInt(0);
                        final int blueValue = jsonNode.get("b").asInt(0);
                        final int whiteValue = jsonNode.get("w").asInt(0);

                        final ObjectNode objectNode = ObjectMapperFactory.JSON.createObjectNode();
                        objectNode.set("uniqueId", new TextNode(uniqueId));
                        objectNode.set("payload", jsonNode);

                        synchronized (Features.MQTT_MESSAGE) {
                            set(Features.MQTT_MESSAGE, LuaUtil.fromJson(jsonNode));
                            triggerEvent(Events.MQTT_RECEIVE_VALUE);
                            awaitEventTrigger(Events.MQTT_RECEIVE_VALUE);
                        }
                    } catch (IOException e) {
                        throw new UnexpectedException(e);
                    }
                }
        );

        return LuaValue.NIL;
    }

    private LuaValue mqttSendValue(LuaValue arg1, LuaValue arg2) {
        LOGGER.info(arg1 + ":" + arg2);

        final String uniqueId = arg1.checkjstring();
        final String state = arg2.checkjstring();

        final String discoveryPrefix = get(Features.MQTT_DISCOVERY_PREFIX).checkjstring();
        final String rootTopic = "%s/%s/%s".formatted(discoveryPrefix, "sensor", uniqueId);

        try {
            mqttClient
                      .publish(
                              rootTopic + "/state",
                              state.getBytes(StandardCharsets.UTF_8)
                      );
        } catch (MqttException e) {
            LOGGER.error("Could not publish state update message for {}", uniqueId, e);
        }

        return LuaValue.NIL;
    }

    private LuaValue mqttReceiveValue(LuaValue arg1) {
        if (!arg1.isnil()) {
            LOGGER.info(name + ": " + arg1.checkjstring());
        }

        return LuaValue.NIL;
    }

    public void addMqttSubscription(MqttTopic mqttTopic) {
        mqttTopics.add(mqttTopic);
    }

    public List<MqttTopic> getMqttTopics() {
        return mqttTopics;
    }

    public MqttClient getMqttClient() {
        return mqttClient;
    }

    public void setMqttClient(MqttClient mqttClient) {
        this.mqttClient = mqttClient;

        for (MqttTopic mqttTopic : getMqttTopics()) {
            mqttTopic.setMqttClient(mqttClient);
        }
    }

    public enum State {
        STARTING(0),
        OK(1),
        ERROR(2),
        EMERGENCY(4),
        MONITOR(5),
        //
        ;

        private final int value;

        State(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }
    }

    public enum Features implements IFeature {
        UPTIME(0),
        LOG(1),
        STATE(2),
        //
        DATE(5),
        TIME(6),
        DAY_OF_MONTH(7),
        MONTH(8),
        YEAR(9),
        DAY_OF_WEEK(10),
        HOUR(11),
        MINUTE(12),
        TIMESTAMP(13),
        TIME_ZONE(14),
        //
        MQTT_URL(20),
        USE_MQTT(21),
        MQTT_CONNECTION(22),
        //
        MQTT_DISCOVERY(23),
        MQTT_DISCOVERY_PREFIX(24),
        MQTT_MESSAGE(25),
        //
        ;

        private final int index;

        Features(int index) {
            this.index = index;
        }

        @Override
        public int index() {
            return index;
        }
    }

    private enum Methods implements IMethod {
        ADD_TO_LOG(0),
        CLEAR_LOG(1),
        //
        MQTT_SEND_DISCOVERY(20),
        MQTT_SEND_VALUE(21),
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

    public enum Events implements IEvent {
        INIT(0),
        TIME_CHANGE(13),
        //
        MQTT_RECEIVE_VALUE(20),
        //
        ;

        private final int address;

        Events(int address) {
            this.address = address;
        }

        @Override
        public int address() {
            return address;
        }
    }
}
