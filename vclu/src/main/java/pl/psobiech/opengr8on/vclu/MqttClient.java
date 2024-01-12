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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.ScheduledExecutorPingSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.util.Util;
import pl.psobiech.opengr8on.vclu.system.clu.VirtualCLU;
import pl.psobiech.opengr8on.vclu.system.objects.MqttTopic;
import pl.psobiech.opengr8on.vclu.util.TlsUtil;

public class MqttClient implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MqttClient.class);

    private static final String SCHEME_TCP = "tcp";

    public static final int MQTT_QOS_AT_LEAST_ONCE = 1;

    private static final int CONNECTION_TIMEOUT_SECONDS = 4;

    private static final int KEEP_ALIVE_INTERVAL_SECONDS = 10;

    private static final int MAX_INFLIGHT = 64;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(
        4,
        ThreadUtil.threadFactory("mqtt", true)
    );

    private MqttAsyncClient mqttClient;

    public void start(
        String mqttUrl, String name,
        Path caCertificatePath, Path certificatePath, Path keyPath,
        VirtualCLU currentClu
    ) {
        final URI mqttUri = URI.create(mqttUrl);

        try {
            mqttClient = new MqttAsyncClient(
                mqttUrl, name,
                null,
                new ScheduledExecutorPingSender(executor),
                executor
            );

            mqttClient.setManualAcks(true);
            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable throwable) {
                    LOGGER.error("Connection to MQTT {} as {} was lost", mqttClient.getCurrentServerURI(), mqttClient.getClientId(), throwable);

                    onMqttConnectionChange(currentClu);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    for (MqttTopic mqttTopic : currentClu.getMqttTopics()) {
                        mqttTopic.onMessage(
                            topic, message.getPayload(),
                            () ->
                                executor.submit(() -> {
                                    try {
                                        mqttClient.messageArrivedComplete(message.getId(), message.getQos());
                                    } catch (MqttException e) {
                                        LOGGER.error(e.getMessage(), e);
                                    }
                                })
                        );
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken deliveryToken) {
                    // NOP
                }
            });

            final MqttConnectOptions options = createConnectionOptions(mqttUri, caCertificatePath, certificatePath, keyPath);
            for (MqttTopic mqttTopic : currentClu.getMqttTopics()) {
                mqttTopic.setMqttClient(this);
            }

            mqttClient.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    LOGGER.info("Connected to MQTT {} as {}", mqttClient.getCurrentServerURI(), mqttClient.getClientId());

                    onMqttConnectionChange(currentClu);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    LOGGER.error("Could not connect to MQTT {} as {}", mqttClient.getCurrentServerURI(), mqttClient.getClientId(), exception);

                    onMqttConnectionChange(currentClu);
                }
            });
        } catch (MqttException e) {
            throw new UnexpectedException(e);
        }
    }

    private static MqttConnectOptions createConnectionOptions(URI mqttUri, Path caCertificatePath, Path certificatePath, Path keyPath) {
        final MqttConnectOptions options = new MqttConnectOptions();
        options.setConnectionTimeout(CONNECTION_TIMEOUT_SECONDS);
        options.setKeepAliveInterval(KEEP_ALIVE_INTERVAL_SECONDS);
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);
        options.setMaxInflight(MAX_INFLIGHT);

        final String userInfo = mqttUri.getUserInfo();
        if (userInfo != null) {
            final Optional<String[]> userInfoPartsOptional = Util.splitAtLeast(userInfo, ":", 2);
            if (userInfoPartsOptional.isPresent()) {
                final String[] userInfoParts = userInfoPartsOptional.get();

                options.setUserName(userInfoParts[0]);
                options.setPassword(userInfoParts[1].toCharArray());
            }
        }

        if (!SCHEME_TCP.equals(mqttUri.getScheme()) && Files.exists(caCertificatePath)) {
            options.setSocketFactory(
                TlsUtil.createSocketFactory(
                    caCertificatePath,
                    certificatePath, keyPath
                )
            );
        }

        return options;
    }

    private void onMqttConnectionChange(VirtualCLU currentClu) {
        final boolean connected = mqttClient.isConnected();
        currentClu.setMqttConnected(connected);

        if (connected) {
            for (MqttTopic mqttTopic : currentClu.getMqttTopics()) {
                try {
                    subscribe(mqttTopic.getTopicFilters());
                } catch (MqttException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
        }
    }

    public void subscribe(Set<String> topicFilterSet) throws MqttException {
        final String[] topicFilters = topicFilterSet.toArray(String[]::new);

        final int[] qos = new int[topicFilters.length];
        Arrays.fill(qos, MQTT_QOS_AT_LEAST_ONCE);

        mqttClient.subscribe(topicFilters, qos);
    }

    public void subscribe(String topicFilter) throws MqttException {
        mqttClient.subscribe(topicFilter, MQTT_QOS_AT_LEAST_ONCE);
    }

    public void unsubscribe(String topicFilter) throws MqttException {
        mqttClient.unsubscribe(topicFilter);
    }

    public void publish(String topic, String message) throws MqttException {
        mqttClient.publish(
            topic, message.getBytes(StandardCharsets.UTF_8),
            MQTT_QOS_AT_LEAST_ONCE, false
        );
    }

    @Override
    public void close() {
        ThreadUtil.close(executor);

        stop();
    }

    public void stop() {
        if (mqttClient != null) {
            try {
                mqttClient.disconnect();
            } catch (MqttException e) {
                LOGGER.error(e.getMessage(), e);
            }

            IOUtil.closeQuietly(mqttClient);
        }

        mqttClient = null;
    }
}
