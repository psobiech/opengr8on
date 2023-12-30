/*
 * OpenGr8ton, open source extensions to systems based on Grenton devices
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

package pl.psobiech.opengr8on.vclu.objects;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.vclu.VirtualCLU;
import pl.psobiech.opengr8on.vclu.VirtualObject;

public class MqttTopic extends VirtualObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(MqttTopic.class);

    private final VirtualCLU currentClu;

    private final Set<String> topicFilters = new HashSet<>();

    private final LinkedBlockingDeque<Map.Entry<String, byte[]>> messageQueue = new LinkedBlockingDeque<>();

    public MqttTopic(String name, VirtualCLU currentClu) {
        super(name);

        this.currentClu = currentClu;
        currentClu.addMqttSubscription(this);

        methodFunctions.put(0, this::subscribe); // mqttsubscription_subscribe
        methodFunctions.put(1, this::unsubscribe); // mqttsubscription_unsubscribe
        methodFunctions.put(2, this::onNextMessage); // mqttsubscription_nextmessage
        methodFunctions.put(10, this::publish); // mqttsubscription_publish
    }

    @Override
    public void setup() {
        triggerEvent(0); // mqttsubscription_oninit
    }

    private LuaValue subscribe(LuaValue arg1) {
        try {
            final String topic = getTopic();

            topicFilters.add(topic);
            final MqttClient mqttClient = currentClu.getMqttClient();
            if (mqttClient != null) {
                mqttClient.subscribe(topic);
            }

            return LuaValue.TRUE;
        } catch (MqttException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return LuaValue.FALSE;
    }

    private LuaValue unsubscribe(LuaValue arg1) {
        try {
            final String topic = getTopic();

            topicFilters.remove(topic);
            final MqttClient mqttClient = currentClu.getMqttClient();
            if (mqttClient != null) {
                mqttClient.unsubscribe(topic);
            }

            return LuaValue.TRUE;
        } catch (MqttException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return LuaValue.FALSE;
    }

    private LuaValue publish(LuaValue arg1) {
        final MqttClient mqttClient = currentClu.getMqttClient();
        if (mqttClient == null) {
            return LuaValue.FALSE;
        }

        final String topic = getTopic();
        if (isSubscribedTo(topic)) {
            LOGGER.warn("Attempt to publish to a topic that we are subscribed to: {}", topic);

            return LuaValue.FALSE;
        }

        final LuaValue message = removeMessage();

        try {
            mqttClient.publish(
                topic,
                new MqttMessage(
                    String.valueOf(message)
                          .getBytes(StandardCharsets.UTF_8)
                )
            );

            return LuaValue.TRUE;
        } catch (MqttException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return LuaValue.FALSE;
    }

    public void onMessage(String topic, int id, byte[] payload) {
        if (!isSubscribedTo(topic)) {
            return;
        }

        while (!messageQueue.offer(Map.entry(topic, payload))) {
            // TODO: retry/fail logic
            Thread.yield();
        }
    }

    private boolean isSubscribedTo(String topic) {
        for (String topicFilter : topicFilters) {
            if (org.eclipse.paho.client.mqttv3.MqttTopic.isMatched(topicFilter, topic)) {
                return true;
            }
        }

        return false;
    }

    public String getTopic() {
        return String.valueOf(featureValues.get(0).checkstring());
    }

    public Set<String> getTopicFilters() {
        return topicFilters;
    }

    private LuaValue onNextMessage(LuaValue arg1) {
        removeMessage();

        return LuaValue.NIL;
    }

    @Override
    public void loop() {
        final LuaValue currentPayload = getMessage();
        if (currentPayload == null || String.valueOf(currentPayload).isEmpty()) {
            final Entry<String, byte[]> entry = messageQueue.poll();
            if (entry != null) {
                final String key = entry.getKey();
                final String payload = new String(entry.getValue());

                featureValues.put(0, LuaValue.valueOf(key)); // mqttsubscription_topic
                featureValues.put(1, LuaValue.valueOf(payload)); // mqttsubscription_message

                triggerEvent(1); // mqttsubscription_onmessage
            }
        }
    }

    private LuaValue getMessage() {
        return featureValues.get(1); // mqttsubscription_message
    }

    private LuaValue removeMessage() {
        return featureValues.remove(1); // mqttsubscription_message
    }

    @Override
    public void close() {
        // NOP
    }
}
