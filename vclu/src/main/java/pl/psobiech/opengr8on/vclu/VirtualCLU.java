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

package pl.psobiech.opengr8on.vclu;

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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.luaj.vm2.LuaInteger;
import org.luaj.vm2.LuaNumber;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.vclu.objects.MqttTopic;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;

import static org.luaj.vm2.LuaValue.valueOf;

public class VirtualCLU extends VirtualObject implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualCLU.class);

    public static final int INDEX = 0;

    private static final int TIME_CHANGE_EVENT_TRIGGER_DELTA_SECONDS = 60;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

    private final ScheduledExecutorService executor;

    private final List<MqttTopic> mqttTopics = new LinkedList<>();

    private volatile ZonedDateTime currentDateTime = getCurrentDateTime();

    public VirtualCLU(String name) {
        super(name);

        this.executor = ThreadUtil.virtualScheduler(name);

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
        set(Features.TIME_ZONE, valueOf(CLUTimeZone.UTC.value()));

        set(Features.MQTT_URL, valueOf("ssl://localhost:8883"));
        register(Features.USE_MQTT, arg1 -> {
            if (arg1.isnil()) {
                return getValue(Features.USE_MQTT);
            }

            // Sometimes OM uses true/false and sometimes 0/1
            return LuaValue.valueOf(
                LuaUtil.trueish(arg1)
            );
        });
        set(Features.MQTT_CONNECTION, LuaValue.valueOf(false));

        register(Methods.ADD_TO_LOG, this::addToLog);
        register(Methods.CLEAR_LOG, this::clearLog);

        executor.scheduleAtFixedRate(
            () -> {
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

    @Override
    public void setup() {
        set(Features.STATE, LuaValue.valueOf(State.OK.value));

        triggerEvent(Events.INIT);
    }

    public boolean isMqttEnabled() {
        return LuaUtil.trueish(get(Features.USE_MQTT));
    }

    public String getMqttUrl() {
        return LuaUtil.stringify(get(Features.MQTT_URL));
    }

    public void setMqttConnected(boolean value) {
        set(Features.MQTT_CONNECTION, LuaValue.valueOf(value));
    }

    private LuaNumber getUptime(LuaValue arg1) {
        return valueOf(
            TimeUnit.MILLISECONDS.toSeconds(
                runtimeBean.getUptime()
            )
        );
    }

    private LuaString getCurrentDateAsString(LuaValue arg1) {
        return valueOf(
            currentDateTime
                .format(DATE_FORMATTER)
        );
    }

    private LuaString getCurrentTimeAsString(LuaValue arg1) {
        return valueOf(
            currentDateTime
                .format(TIME_FORMATTER)
        );
    }

    private LuaInteger getCurrentDayOfMonth(LuaValue arg1) {
        return valueOf(
            currentDateTime
                .getDayOfMonth()
        );
    }

    private LuaInteger getCurrentMonth(LuaValue arg1) {
        return valueOf(
            currentDateTime
                .getMonthValue()
        );
    }

    private LuaInteger getCurrentYear(LuaValue arg1) {
        return valueOf(
            currentDateTime
                .getYear()
        );
    }

    private LuaInteger getCurrentDayOfWeek(LuaValue arg1) {
        return valueOf(
            currentDateTime
                .getDayOfWeek()
                .getValue()
        );
    }

    private LuaInteger getCurrentHour(LuaValue arg1) {
        return valueOf(
            currentDateTime
                .getHour()
        );
    }

    private LuaInteger getCurrentMinute(LuaValue arg1) {
        return valueOf(
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

    private LuaNumber getCurrentEpochSeconds(LuaValue arg1) {
        return valueOf(
            currentDateTime.toInstant()
                           .getEpochSecond()
        );
    }

    private LuaValue addToLog(LuaValue arg) {
        set(Features.LOG, arg);

        if (!arg.isnil()) {
            LOGGER.info(name + ": " + arg.checkjstring());
        }

        return LuaValue.NIL;
    }

    private LuaValue clearLog(LuaValue arg) {
        set(Features.LOG, LuaValue.NIL);

        return LuaValue.NIL;
    }

    public void addMqttSubscription(MqttTopic mqttTopic) {
        mqttTopics.add(mqttTopic);
    }

    public List<MqttTopic> getMqttTopics() {
        return mqttTopics;
    }

    @Override
    public void close() {
        ThreadUtil.close(executor);
    }

    private enum State {
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
    }

    private enum Features implements IFeature {
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

    private enum Events implements IEvent {
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
