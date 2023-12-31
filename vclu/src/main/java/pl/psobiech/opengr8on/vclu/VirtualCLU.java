/*
 * OpenGr8on, open source extensions to systems based on Grenton devices
 * Copyright (C) 2023 Piotr Sobiech
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pl.psobiech.opengr8on.vclu;

import java.io.Closeable;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.Inet4Address;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.luaj.vm2.LuaInteger;
import org.luaj.vm2.LuaNumber;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.CLUFiles;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.util.Util;
import pl.psobiech.opengr8on.vclu.objects.MqttTopic;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;

import static org.luaj.vm2.LuaValue.valueOf;

public class VirtualCLU extends VirtualObject implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualCLU.class);

    private static final int UTC_TIMEZONE_ID = 22;

    private final static Map<Integer, ZoneId> TIME_ZONES = Map.ofEntries(
        Map.entry(0, ZoneId.of("Europe/Warsaw")),
        Map.entry(1, ZoneId.of("Europe/London")),
        Map.entry(2, ZoneId.of("Europe/Moscow")),
        Map.entry(3, ZoneId.of("Europe/Istanbul")),
        Map.entry(4, ZoneId.of("Europe/Athens")),
        Map.entry(5, ZoneId.of("Asia/Dubai")),
        Map.entry(6, ZoneId.of("Asia/Jakarta")),
        Map.entry(7, ZoneId.of("Asia/Hong_Kong")),
        Map.entry(8, ZoneId.of("Australia/Sydney")),
        Map.entry(9, ZoneId.of("Australia/Perth")),
        Map.entry(10, ZoneId.of("Australia/Brisbane")),
        Map.entry(11, ZoneId.of("Pacific/Auckland")),
        Map.entry(12, ZoneId.of("Pacific/Honolulu")),
        Map.entry(13, ZoneId.of("America/Anchorage")),
        Map.entry(14, ZoneId.of("America/Chicago")),
        Map.entry(15, ZoneId.of("America/New_York")),
        Map.entry(16, ZoneId.of("America/Barbados")),
        Map.entry(17, ZoneId.of("America/Sao_Paulo")),
        Map.entry(18, ZoneId.of("America/Bogota")),
        Map.entry(19, ZoneId.of("America/Buenos_Aires")),
        Map.entry(20, ZoneId.of("America/Chicago")),
        Map.entry(21, ZoneId.of("America/Los_Angeles")),
        Map.entry(UTC_TIMEZONE_ID, ZoneOffset.UTC)
    );

    private final Path aDriveDirectory;

    private final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

    private final ScheduledExecutorService executorService;

    private final List<MqttTopic> mqttTopics = new LinkedList<>();

    private volatile ZonedDateTime currentDateTime;

    private MqttClient mqttClient;

    public VirtualCLU(String name, Inet4Address address, Path aDriveDirectory) {
        super(name);

        this.executorService = Executors.newScheduledThreadPool(
            1,
            ThreadUtil.daemonThreadFactory("cluObject_" + name)
        );

        this.aDriveDirectory = aDriveDirectory;

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
        set(Features.TIME_ZONE, valueOf(UTC_TIMEZONE_ID));

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
        register(Features.MQTT_CONNECTION, arg1 ->
            LuaValue.valueOf(
                mqttClient != null && mqttClient.isConnected()
            )
        );

        register(Methods.ADD_TO_LOG, this::addToLog);
        register(Methods.CLEAR_LOG, this::clearLog);

        currentDateTime = getCurrentDateTime();
        executorService.scheduleAtFixedRate(
            () -> {
                final ZonedDateTime lastDateTime = currentDateTime;
                currentDateTime = getCurrentDateTime();

                if (!currentDateTime.getZone().equals(lastDateTime.getZone())
                    || Duration.between(lastDateTime, currentDateTime).abs().getSeconds() >= 60) {
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

    @Override
    public void loop() {
        final boolean mqttEnable = get(Features.USE_MQTT).checkboolean();
        final boolean mqttAlreadyEnabled = mqttClient != null;
        if (mqttEnable ^ mqttAlreadyEnabled) {
            disableMqtt();

            if (mqttEnable) {
                enableMqtt();
            }
        }
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
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        );
    }

    private LuaString getCurrentTimeAsString(LuaValue arg1) {
        return valueOf(
            currentDateTime
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
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

        return TIME_ZONES.getOrDefault(zoneIdLuaValue.checkint(), ZoneOffset.UTC);
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

    private void disableMqtt() {
        if (mqttClient != null) {
            try {
                mqttClient.disconnect();
            } catch (MqttException e) {
                throw new UnexpectedException(e);
            }

            FileUtil.closeQuietly(mqttClient);
        }

        mqttClient = null;
    }

    private void enableMqtt() {
        // TODO: manage the client connection and topics
        // TODO: expose some LUA API to publish/subscribe
        final String mqttUrl = get(Features.MQTT_URL).checkjstring();

        final URI mqttUri = URI.create(mqttUrl);

        try {
            mqttClient = new MqttClient(mqttUrl, name, null);
            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable throwable) {
                    LOGGER.error("MQTT connectionLost", throwable);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    LOGGER.info("MQTT messageArrived: {} / {}", topic, message);

                    for (MqttTopic mqttTopic : mqttTopics) {
                        mqttTopic.onMessage(topic, message.getId(), message.getPayload());
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken deliveryToken) {
                    LOGGER.info("MQTT deliveryComplete: {}", deliveryToken);
                }
            });

            final MqttConnectOptions options = new MqttConnectOptions();
            options.setConnectionTimeout(60);
            options.setKeepAliveInterval(60);
            options.setAutomaticReconnect(true);
            options.setCleanSession(false);

            final String userInfo = mqttUri.getUserInfo();
            if (userInfo != null) {
                final Optional<String[]> userInfoPartsOptional = Util.splitAtLeast(userInfo, ":", 2);
                if (userInfoPartsOptional.isPresent()) {
                    final String[] userInfoParts = userInfoPartsOptional.get();

                    options.setUserName(userInfoParts[0]);
                    options.setPassword(userInfoParts[1].toCharArray());
                }
            }

            final Path caCertificatePath = aDriveDirectory.resolve(CLUFiles.MQTT_ROOT_PEM.getFileName());
            if (!mqttUri.getScheme().equals("tcp") && Files.exists(caCertificatePath)) {
                options.setSocketFactory(
                    TlsUtil.getSocketFactory(
                        caCertificatePath,
                        aDriveDirectory.resolve(CLUFiles.MQTT_PUBLIC_CRT.getFileName()),
                        aDriveDirectory.resolve(CLUFiles.MQTT_PRIVATE_PEM.getFileName())
                    )
                );
            }

            mqttClient.connect(options);
            LOGGER.info("Connected to MQTT {} as {}", mqttClient.getCurrentServerURI(), mqttClient.getClientId());

            for (MqttTopic mqttTopic : mqttTopics) {
                mqttClient.subscribe(mqttTopic.getTopicFilters().toArray(String[]::new));
            }
        } catch (MqttException e) {
            throw new UnexpectedException(e);
        }
    }

    public void addMqttSubscription(MqttTopic mqttTopic) {
        mqttTopics.add(mqttTopic);
    }

    public MqttClient getMqttClient() {
        return mqttClient;
    }

    @Override
    public void close() {
        super.close();

        executorService.shutdown();

        if (mqttClient != null) {
            try {
                mqttClient.disconnect();
            } catch (MqttException e) {
                LOGGER.warn(e.getMessage(), e);
            }
        }

        FileUtil.closeQuietly(mqttClient);
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
