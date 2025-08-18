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

import java.io.Closeable;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.luaj.vm2.LuaInteger;
import org.luaj.vm2.LuaNumber;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.vclu.system.VirtualSystem;
import pl.psobiech.opengr8on.vclu.system.objects.clu.CLUTimeZone;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;

public class VirtualCLU extends VirtualObject implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualCLU.class);

    public static final int INDEX = 0;

    private static final int TIME_CHANGE_EVENT_TRIGGER_DELTA_SECONDS = 60;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

    private final List<MqttTopic> mqttTopics = new LinkedList<>();

    private volatile ZonedDateTime currentDateTime = getCurrentDateTime();

    public VirtualCLU(VirtualSystem virtualSystem, String name) {
        super(
                virtualSystem, name,
                Features.class, Methods.class, Events.class
        );

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
        registerBoolean(Features.USE_MQTT);
        set(Features.MQTT_CONNECTION, LuaValue.valueOf(false));

        registerBoolean(Features.MQTT_DISCOVERY);
        set(Features.MQTT_DISCOVERY_PREFIX, LuaValue.valueOf("homeassistant"));

        register(Methods.ADD_TO_LOG, this::addToLog);
        register(Methods.CLEAR_LOG, this::clearLog);

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

    public void addMqttSubscription(MqttTopic mqttTopic) {
        mqttTopics.add(mqttTopic);
    }

    public List<MqttTopic> getMqttTopics() {
        return mqttTopics;
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
