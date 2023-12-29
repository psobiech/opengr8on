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
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
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

    private volatile ZonedDateTime currentDateTime;

    private MqttClient mqttClient;

    public VirtualCLU(String name, Inet4Address address, Path aDriveDirectory) {
        super(name);

        this.executorService = Executors.newScheduledThreadPool(1, ThreadUtil.daemonThreadFactory("cluObject_" + name));

        this.aDriveDirectory = aDriveDirectory;

        featureFunctions.put(0, this::getUptime); // clu_uptime
        VirtualCLU.this.featureValues.put(2, valueOf(0)); // clu_state
        featureFunctions.put(5, this::getCurrentDateAsString); // clu_date
        featureFunctions.put(6, this::getCurrentTimeAsString); // clu_time
        featureFunctions.put(7, this::getCurrentDayOfMonth); // clu_day
        featureFunctions.put(8, this::getCurrentMonth); // clu_month
        featureFunctions.put(9, this::getCurrentYear); // clu_year
        featureFunctions.put(10, this::getCurrentDayOfWeek); // clu_dayofweek
        featureFunctions.put(11, this::getCurrentHour); // clu_hour
        featureFunctions.put(12, this::getCurrentMinute); // clu_minute
        featureFunctions.put(13, this::getCurrentEpochSeconds); // clu_localtime
        VirtualCLU.this.featureValues.put(14, valueOf(UTC_TIMEZONE_ID)); // clu_timezone
        VirtualCLU.this.featureValues.put(20, valueOf("ssl://localhost:8883")); // clu_mqtturl
        featureFunctions.put(21, arg1 -> { // clu_usemqtt
            if (arg1.isnil()) {
                return VirtualCLU.this.featureValues.get(21);
            }

            final boolean mqttEnable = (arg1.isboolean() && arg1.checkboolean()) || (arg1.isint() && arg1.checkint() != 0);
            final boolean mqttAlreadyEnabled = mqttClient != null;
            if (mqttEnable ^ mqttAlreadyEnabled) {
                disableMqtt();

                if (mqttEnable) {
                    enableMqtt();
                }
            }

            return LuaValue.valueOf(mqttEnable);
        });
        featureFunctions.put(22, arg1 -> { // clu_mqttconnection
            return LuaValue.valueOf(
                mqttClient != null && mqttClient.isConnected()
            );
        });

        methodFunctions.put(0, this::addToLog); // clu_addtolog
        methodFunctions.put(1, this::clearLog); // clu_clearlog

        currentDateTime = getCurrentDateTime();
        executorService.scheduleAtFixedRate(
            () -> {
                final ZonedDateTime lastDateTime = currentDateTime;
                currentDateTime = getCurrentDateTime();

                if (Duration.between(lastDateTime, currentDateTime).abs().getSeconds() >= 60) {
                    triggerEvent(13); // clu_ontimechange
                }
            },
            1, 1, TimeUnit.SECONDS
        );
    }

    @Override
    public void setup() {
        VirtualCLU.this.featureValues.put(2, valueOf(1)); // clu_state

        triggerEvent(0); // clu_oninit
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
        final LuaValue zoneIdLuaValue = VirtualCLU.this.featureValues.get(14);
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
        VirtualCLU.this.featureValues.put(1, arg);

        if (!arg.isnil()) {
            final String logValue = String.valueOf(arg.checkstring());
            LOGGER.info(VirtualCLU.this.name + ": " + logValue);
        }

        return LuaValue.NIL;
    }

    private LuaValue clearLog(LuaValue arg) {
        VirtualCLU.this.featureValues.put(1, LuaValue.NIL);

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
        final String mqttUrl = String.valueOf(VirtualCLU.this.featureValues.get(20).checkstring());

        try {
            LOGGER.info("Connecting to MQTT on: {}", mqttUrl);
            mqttClient = new MqttClient(mqttUrl, name, null);
            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable throwable) {
                    LOGGER.error("MQTT connectionLost", throwable);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    LOGGER.info("MQTT messageArrived: {} / {}", topic, message);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken deliveryToken) {
                    LOGGER.info("MQTT deliveryComplete: {}", deliveryToken);
                }
            });

            final MqttConnectOptions options = new MqttConnectOptions();
            options.setConnectionTimeout(60);
            options.setAutomaticReconnect(true);
            options.setKeepAliveInterval(60);
            options.setSocketFactory(
                TlsUtil.getSocketFactory(
                    aDriveDirectory.resolve(CLUFiles.MQTT_ROOT_PEM.getFileName()),
                    aDriveDirectory.resolve(CLUFiles.MQTT_PUBLIC_CRT.getFileName()),
                    aDriveDirectory.resolve(CLUFiles.MQTT_PRIVATE_PEM.getFileName())
                )
            );

            mqttClient.connect(options);
            mqttClient.subscribe("topic", 0);
        } catch (MqttException e) {
            throw new UnexpectedException(e);
        }
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
}
