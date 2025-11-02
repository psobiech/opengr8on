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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.*;
import pl.psobiech.opengr8on.vclu.system.objects.MqttTopic;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.vclu.util.TlsUtil;

import java.io.Closeable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

public class MqttClient implements Closeable {
    public static final int MQTT_QOS_AT_LEAST_ONCE = 1;

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttClient.class);

    private static final String SCHEME_TCP = "tcp";

    private static final int CONNECTION_TIMEOUT_SECONDS = 4;

    private static final int KEEP_ALIVE_INTERVAL_SECONDS = 10;

    private static final int MAX_INFLIGHT = 256;

    // the mqtt client requires at least 4 threads (also, it does not support virtual threads)
    private final ScheduledExecutorService executor = ThreadUtil.daemonScheduler(5, "MQTT");

    private final Map<String, List<Consumer<byte[]>>> mqttSubscriptions = new Hashtable<>();

    private MqttAsyncClient mqttClient;

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

    public boolean isStarted() {
        return mqttClient != null;
    }

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
                    onMqttConnectionChange(currentClu, null);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    try {
                        for (MqttTopic mqttTopic : currentClu.getMqttTopics()) {
                            try {
                                mqttTopic.onMessage(
                                        topic, message.getPayload(), () -> {
                                        }
                                );
                            } catch (Exception e) {
                                LOGGER.error(e.getMessage(), e);
                            }
                        }

                        final List<Consumer<byte[]>> consumers = mqttSubscriptions.getOrDefault(topic, Collections.emptyList());
                        for (Consumer<byte[]> consumer : consumers) {
                            try {
                                consumer.accept(message.getPayload());
                            } catch (Exception e) {
                                LOGGER.error(e.getMessage(), e);
                            }
                        }
                    } finally {
                        executor.submit(() -> {
                            try {
                                mqttClient.messageArrivedComplete(message.getId(), message.getQos());
                            } catch (MqttException e) {
                                LOGGER.error(e.getMessage(), e);
                            }
                        });
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken deliveryToken) {
                    // NOP
                }
            });

            final MqttConnectOptions options = createConnectionOptions(mqttUri, caCertificatePath, certificatePath, keyPath);

            mqttClient.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    onMqttConnectionChange(currentClu, null);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    onMqttConnectionChange(currentClu, exception);
                }
            });
        } catch (MqttException e) {
            throw new UnexpectedException(e);
        }
    }

    private void onMqttConnectionChange(VirtualCLU currentClu, Throwable exception) {
        final boolean connected = mqttClient.isConnected();
        LOGGER.debug("MQTT {} Connected: {}", mqttClient.getClientId(), connected, exception);

        currentClu.setMqttConnected(connected);
    }

    public void subscribe(Set<String> topicFilterSet) throws MqttException {
        final String[] topicFilters = topicFilterSet.toArray(String[]::new);

        final int[] qos = new int[topicFilters.length];
        Arrays.fill(qos, MQTT_QOS_AT_LEAST_ONCE);

        LOGGER.trace("MQTT {} Subscribe: {} / MQTT_QOS_AT_LEAST_ONCE", mqttClient.getClientId(), Arrays.toString(topicFilters));

        mqttClient.subscribe(topicFilters, qos);
    }

    public void subscribe(String topicFilter, Consumer<byte[]> consumer) {
        mqttSubscriptions.computeIfAbsent(topicFilter, ignored -> new ArrayList<>())
                         .add(consumer);

        try {
            subscribe(topicFilter);
        } catch (MqttException e) {
            throw new UnexpectedException(e);
        }
    }

    public void subscribe(String topicFilter) throws MqttException {
        LOGGER.trace("MQTT {} Subscribe: {} / MQTT_QOS_AT_LEAST_ONCE", mqttClient.getClientId(), topicFilter);

        mqttClient.subscribe(topicFilter, MQTT_QOS_AT_LEAST_ONCE);
    }

    public void unsubscribe(String topicFilter) throws MqttException {
        LOGGER.trace("MQTT {} Unsubscribe: {}", mqttClient.getClientId(), topicFilter);

        mqttClient.unsubscribe(topicFilter);
    }

    public void tryPublish(String topic, Object payloadObject) {
        tryPublish(topic, payloadObject, false);
    }

    public void tryPublish(String topic, Object payloadObject, boolean retain) {
        final byte[] payload;
        try {
            payload = parsePayload(payloadObject);
        } catch (RuntimeException e) {
            LOGGER.error("Could not publish message to topic {}", topic, e);

            return;
        }

        try {
            publish(topic, payload, retain);
        } catch (MqttException | RuntimeException e) {
            LOGGER.error("Could not publish message ({}) to topic {}", HexUtil.asString(payload), topic, e);
        }
    }

    public static byte[] parsePayload(Object payloadObject) {
        try {
            if (payloadObject instanceof String objectAsString) {
                return objectAsString.getBytes(StandardCharsets.UTF_8);
            }

            if (payloadObject instanceof byte[] payloadAsBytes) {
                return payloadAsBytes;
            }

            return ObjectMapperFactory.JSON.writeValueAsBytes(payloadObject);
        } catch (JsonProcessingException e) {
            throw new UnexpectedException(e);
        }
    }

    public int publish(String topic, byte[] payload) throws MqttException {
        return publish(topic, payload, false);
    }

    public int publish(String topic, byte[] payload, boolean retained) throws MqttException {
        LOGGER.trace("MQTT {} Publish: {} / {}", mqttClient.getClientId(), topic, ToStringUtil.toString(payload));

        final IMqttDeliveryToken publish = mqttClient.publish(
                topic, payload,
                MQTT_QOS_AT_LEAST_ONCE, retained
        );

        if (publish == null) {
            throw new UnexpectedException("mqtt publish returned null");
        }

        return publish.getMessageId();
    }

    @Override
    public void close() {
        stop();

        ThreadUtil.closeQuietly(executor);
    }

    public void stop() {
        if (mqttClient != null) {
            try {
                mqttClient.disconnect();
            } catch (MqttException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        IOUtil.closeQuietly(mqttClient);
        mqttClient = null;
    }
}
