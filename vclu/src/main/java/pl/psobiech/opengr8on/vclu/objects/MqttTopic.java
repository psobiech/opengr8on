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

        register(Methods.SUBSCRIBE, this::subscribe); // mqttsubscription_subscribe
        register(Methods.UNSUBSCRIBE, this::unsubscribe); // mqttsubscription_unsubscribe
        register(Methods.NEXT_MESSAGE, this::onNextMessage); // mqttsubscription_nextmessage
        register(Methods.PUBLISH, this::publish); // mqttsubscription_publish
    }

    @Override
    public void setup() {
        triggerEvent(Events.INIT);
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

        final LuaValue message = clearMessage();

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
        return String.valueOf(get(Features.TOPIC).checkstring());
    }

    public Set<String> getTopicFilters() {
        return topicFilters;
    }

    private LuaValue onNextMessage(LuaValue arg1) {
        clearMessage();

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

                set(Features.TOPIC, LuaValue.valueOf(key));
                set(Features.MESSAGE, LuaValue.valueOf(payload));

                triggerEvent(Events.MESSAGE);
            }
        }
    }

    private LuaValue getMessage() {
        return get(Features.MESSAGE);
    }

    private LuaValue clearMessage() {
        return clear(Features.MESSAGE);
    }

    private enum Features implements IFeature {
        TOPIC(0),
        MESSAGE(1),
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
        SUBSCRIBE(0),
        UNSUBSCRIBE(1),
        NEXT_MESSAGE(2),
        PUBLISH(10),
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
        MESSAGE(1),
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
