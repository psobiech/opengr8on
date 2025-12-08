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
import com.fasterxml.jackson.databind.JsonNode;
import com.hivemq.client.mqtt.*;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.lifecycle.MqttClientAutoReconnect;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5Connect;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.unsubscribe.Mqtt5Unsubscribe;
import io.reactivex.internal.schedulers.ExecutorScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.*;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.vclu.util.TlsUtil;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static pl.psobiech.opengr8on.util.ThreadUtil.await;
import static pl.psobiech.opengr8on.util.ThreadUtil.awaitQuietly;
import static pl.psobiech.opengr8on.vclu.system.objects.remoteclu.devices.RemoteCLUDevice.availabilityTopic;

public class MqttClient implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttClient.class);

    private static final String SCHEME_TCP = "tcp";

    private static final int CONNECTION_TIMEOUT_SECONDS = 4;

    private static final int KEEP_ALIVE_INTERVAL_SECONDS = 10;

    public static final String OFFLINE = "offline";

    public static final String ONLINE = "online";

    // the mqtt client requires at least 4 threads (also, it does not support virtual threads)
    private final ScheduledExecutorService executor = ThreadUtil.virtualScheduler("MQTT");

    private Mqtt5AsyncClient mqttClient;

    public boolean isInitialized() {
        return mqttClient != null;
    }

    public void start(
            String mqttUrl, String name,
            Path caCertificatePath, Path clientCertificatePath, Path clientKeyPath,
            VirtualCLU virtualClu
    ) {
        final URI mqttUri = URI.create(mqttUrl);

        final Mqtt5SimpleAuth simpleAuth = createAuthentication(mqttUri);
        final MqttClientTransportConfig transportConfig = createTransportConfiguration(caCertificatePath, clientCertificatePath, clientKeyPath, mqttUri);

        this.mqttClient = create(name, virtualClu, transportConfig);

        await(
                mqttClient.connect(
                        Mqtt5Connect.builder()
                                    .simpleAuth(simpleAuth)
                                    .cleanStart(false)
                                    .keepAlive(KEEP_ALIVE_INTERVAL_SECONDS)
                                    .willPublish(
                                            Mqtt5Publish.builder()
                                                        .topic(availabilityTopic())
                                                        .payload(formatPayload(OFFLINE))
                                                        .retain(true)
                                                        .asWill()
                                                        .delayInterval(KEEP_ALIVE_INTERVAL_SECONDS)
                                                        .build()
                                    )
                                    .build()
                )
        );

        mqttClient.publishes(
                MqttGlobalPublishFilter.UNSOLICITED,
                mqtt5Publish -> LOGGER.warn("Unsolicited MQTT message: {}", mqtt5Publish),
                true
        );

        mqttClient.publishes(
                MqttGlobalPublishFilter.REMAINING,
                mqtt5Publish -> LOGGER.warn("Unhandled MQTT message: {}", mqtt5Publish),
                true
        );

        await(
                mqttClient.publish(
                        Mqtt5Publish.builder()
                                    .topic(availabilityTopic())
                                    .payload(formatPayload(ONLINE))
                                    .retain(true)
                                    .build()
                )
        );
    }

    private Mqtt5AsyncClient create(String name, VirtualCLU virtualClu, MqttClientTransportConfig transportConfig) {
        return Mqtt5Client.builder()
                          .identifier("%s/%s-%s".formatted(name, ServerVersion.get(), UUID.randomUUID()))
                          .transportConfig(
                                  transportConfig
                          )
                          .automaticReconnect(
                                  MqttClientAutoReconnect.builder()
                                                         .build()
                          )
                          .executorConfig(
                                  MqttClientExecutorConfig.builder()
                                                          .applicationScheduler(
                                                                  new ExecutorScheduler(executor, true)
                                                          )
                                                          .nettyExecutor(executor)
                                                          .build()
                          )
                          .addConnectedListener(context -> onMqttConnectionChange(virtualClu, null))
                          .addDisconnectedListener(context -> onMqttConnectionChange(virtualClu, context.getCause()))
                          .buildAsync();
    }

    private static MqttClientTransportConfig createTransportConfiguration(Path caCertificatePath, Path clientCertificatePath, Path clientKeyPath, URI mqttUri) {
        final MqttClientSslConfig mqttClientSslConfig = createSslConfiguration(caCertificatePath, clientCertificatePath, clientKeyPath, mqttUri);
        final MqttWebSocketConfig webSocketConfig = createWebSocketConfiguration(mqttUri);

        return MqttClientTransportConfig.builder()
                                        .webSocketWithDefaultConfig()
                                        .webSocketConfig(webSocketConfig)
                                        .sslWithDefaultConfig()
                                        .sslConfig(mqttClientSslConfig)
                                        .serverHost(mqttUri.getHost())
                                        .serverPort(mqttUri.getPort())
                                        .socketConnectTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                        .mqttConnectTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                        .build();
    }

    private static MqttWebSocketConfig createWebSocketConfiguration(URI mqttUri) {
        if (mqttUri.getScheme().startsWith("ws")) {
            return MqttWebSocketConfig.builder()
                                      .build();
        }

        return null;
    }

    private static MqttClientSslConfig createSslConfiguration(Path caCertificatePath, Path clientCertificatePath, Path clientKeyPath, URI mqttUri) {
        if (SCHEME_TCP.equals(mqttUri.getScheme()) || !Files.exists(caCertificatePath)) {
            return null;
        }

        final KeyManagerFactory clientKeyManagerFactory;
        final TrustManagerFactory caTrustManagerFactory;
        try {
            final KeyStore caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            caKeyStore.load(null, null);
            caKeyStore.setCertificateEntry("certificate", TlsUtil.readCertificate(caCertificatePath));

            final KeyStore clientKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            clientKeyStore.load(null, null);
            if (Files.exists(clientCertificatePath) && Files.exists(clientKeyPath)) {
                final X509Certificate clientCertificate = TlsUtil.readCertificate(clientCertificatePath);
                clientKeyStore.setCertificateEntry("certificate", clientCertificate);
                clientKeyStore.setKeyEntry("key", TlsUtil.readPrivateKey(clientKeyPath), null, new java.security.cert.Certificate[]{clientCertificate});
            }

            clientKeyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            clientKeyManagerFactory.init(clientKeyStore, null);

            caTrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            caTrustManagerFactory.init(caKeyStore);
        } catch (CertificateException | KeyStoreException | IOException | NoSuchAlgorithmException |
                 UnrecoverableKeyException e) {
            throw new UnexpectedException("Could not initialize SSL context", e);
        }

        return MqttClientSslConfig.builder()
                                  .trustManagerFactory(caTrustManagerFactory)
                                  .keyManagerFactory(clientKeyManagerFactory)
                                  .build();

    }

    private static Mqtt5SimpleAuth createAuthentication(URI mqttUri) {
        final String userInfo = mqttUri.getUserInfo();
        if (userInfo != null) {
            final Optional<String[]> userInfoPartsOptional = Util.splitAtLeast(userInfo, ":", 2);
            if (userInfoPartsOptional.isPresent()) {
                final String[] userInfoParts = userInfoPartsOptional.get();

                return Mqtt5SimpleAuth.builder()
                                      .username(userInfoParts[0])
                                      .password(userInfoParts[1].getBytes(StandardCharsets.UTF_8))
                                      .build();
            }
        }

        return null;
    }

    private void onMqttConnectionChange(VirtualCLU virtualClu, Throwable exception) {
        final boolean connected = mqttClient.getState().isConnected();
        LOGGER.debug("MQTT {} Connected: {}", mqttClient.getConfig().getClientIdentifier(), connected, exception);

        virtualClu.setMqttConnected(connected);
    }

    public void subscribeWithManualAck(String topicFilter, BiConsumer<byte[], Runnable> consumer) {
        LOGGER.trace("MQTT {} Subscribe: {} / MQTT_QOS_AT_LEAST_ONCE", mqttClient.getConfig().getClientIdentifier(), topicFilter);

        await(
                mqttClient.subscribe(
                        Mqtt5Subscribe.builder()
                                      .topicFilter(topicFilter)
                                      .qos(MqttQos.AT_LEAST_ONCE)
                                      .build(),
                        mqtt5Publish -> consumer.accept(mqtt5Publish.getPayloadAsBytes(), mqtt5Publish::acknowledge)
                )
        );
    }

    public void subscribe(String topicFilter, Consumer<byte[]> consumer) {
        LOGGER.trace("MQTT {} Subscribe: {} / MQTT_QOS_AT_LEAST_ONCE", mqttClient.getConfig().getClientIdentifier(), topicFilter);

        await(
                mqttClient.subscribe(
                        Mqtt5Subscribe.builder()
                                      .topicFilter(topicFilter)
                                      .qos(MqttQos.AT_LEAST_ONCE)
                                      .build(),
                        mqtt5Publish ->
                                consumer.accept(mqtt5Publish.getPayloadAsBytes())
                )
        );
    }

    public void unsubscribe(String topicFilter) {
        LOGGER.trace("MQTT {} Unsubscribe: {}", mqttClient.getConfig().getClientIdentifier(), topicFilter);

        await(
                mqttClient.unsubscribe(
                        Mqtt5Unsubscribe.builder()
                                        .topicFilter(topicFilter)
                                        .build()
                )
        );
    }

    public void tryPublish(String topic, Object payloadObject) {
        tryPublish(topic, payloadObject, false);
    }

    public void tryPublish(String topic, Object payloadObject, boolean retain) {
        final byte[] payload;
        try {
            payload = formatPayload(payloadObject);
        } catch (RuntimeException e) {
            LOGGER.error("Could not publish message to topic {}", topic, e);

            return;
        }

        try {
            publish(topic, payload, retain);
        } catch (RuntimeException e) {
            LOGGER.error("Could not publish message ({}) to topic {}", HexUtil.asString(payload), topic, e);
        }
    }

    private static byte[] formatPayload(Object payloadObject) {
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

    public void publish(String topic, String payload) {
        publish(topic, formatPayload(payload), false);
    }

    public void publish(String topic, JsonNode payload) {
        publish(topic, formatPayload(payload), false);
    }

    public void publish(String topic, byte[] payload) {
        publish(topic, payload, false);
    }

    public void publish(String topic, byte[] payload, boolean retained) {
        LOGGER.trace("MQTT {} Publish: {} / {}", mqttClient.getConfig().getClientIdentifier(), topic, ToStringUtil.toString(payload));

        awaitQuietly(
                mqttClient.publish(
                        Mqtt5Publish.builder()
                                    .topic(topic)
                                    .payload(payload)
                                    .retain(retained)
                                    .build()
                )
        );
    }

    @Override
    public void close() {
        stop();

        ThreadUtil.closeQuietly(executor);
    }

    public void stop() {
        if (mqttClient != null) {
            mqttClient.disconnect();
        }

        mqttClient = null;
    }
}
