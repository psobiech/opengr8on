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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.vclu.MqttClient;
import pl.psobiech.opengr8on.vclu.system.VirtualSystem;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;

public class MqttTopic extends VirtualObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(MqttTopic.class);

    public static final int INDEX = 999;

    private final VirtualSystem virtualSystem;

    private final Set<String> topicFilters = new HashSet<>();

    private final LinkedBlockingDeque<Map.Entry<String, Message>> messageQueue = new LinkedBlockingDeque<>();

    private MqttClient mqttClient;

    public MqttTopic(String name, VirtualSystem virtualSystem) {
        super(
            name,
            Features.class, Methods.class, Events.class
        );

        this.virtualSystem = virtualSystem;

        register(Methods.SUBSCRIBE, this::subscribe);
        register(Methods.UNSUBSCRIBE, this::unsubscribe);
        register(Methods.NEXT_MESSAGE, this::onNextMessage);
        register(Methods.PUBLISH, this::publish);
    }

    public void setMqttClient(MqttClient mqttClient) {
        this.mqttClient = mqttClient;
    }

    @Override
    public void setup() {
        virtualSystem.getCurrentClu()
                     .addMqttSubscription(this);

        triggerEvent(Events.INIT);
    }

    private LuaValue subscribe(LuaValue arg1) {
        try {
            final String topic = arg1.checkjstring();

            topicFilters.add(topic);
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
            final String topic = arg1.checkjstring();

            topicFilters.remove(topic);
            if (mqttClient != null) {
                mqttClient.unsubscribe(topic);
            }

            return LuaValue.TRUE;
        } catch (MqttException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return LuaValue.FALSE;
    }

    private LuaValue publish(LuaValue arg1, LuaValue arg2) {
        if (mqttClient == null) {
            return LuaValue.FALSE;
        }

        final String topic = arg1.checkjstring();
        if (isSubscribedTo(topic)) {
            LOGGER.warn("Attempt to publish to a topic that we are subscribed to: {}", topic);

            return LuaValue.FALSE;
        }

        try {
            mqttClient.publish(topic, asPayload(arg2));

            return LuaValue.TRUE;
        } catch (IOException | MqttException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return LuaValue.FALSE;
    }

    private static byte[] asPayload(LuaValue luaValue) throws JsonProcessingException {
        if (luaValue.istable()) {
            return ObjectMapperFactory.JSON.writeValueAsBytes(LuaUtil.asObject(luaValue));
        }

        return luaValue.checkjstring()
                       .getBytes(StandardCharsets.UTF_8);
    }

    public void onMessage(String topic, byte[] payload, Runnable acknowledged) {
        if (!isSubscribedTo(topic)) {
            return;
        }

        while (!messageQueue.offer(Map.entry(topic, new Message(payload, acknowledged)))) {
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

    public Set<String> getTopicFilters() {
        return topicFilters;
    }

    private LuaValue onNextMessage() {
        clearTopic();
        clearMessage();

        return LuaValue.NIL;
    }

    @Override
    public void loop() {
        final String currentPayload = getMessage();
        if (currentPayload == null || currentPayload.isEmpty()) {
            final Entry<String, Message> entry = messageQueue.poll();
            if (entry != null) {
                final String topic = entry.getKey();
                final Message message = entry.getValue();

                set(Features.TOPIC, LuaValue.valueOf(topic));
                set(Features.MESSAGE, fromPayload(message));
                if (triggerEvent(Events.MESSAGE)) {
                    message.acknowledgement()
                           .run();
                }
            }
        }
    }

    private static LuaValue fromPayload(Message message) {
        final byte[] messagePayload = message.payload();

        try {
            final JsonNode jsonNode = ObjectMapperFactory.JSON.readTree(messagePayload);
            if (jsonNode.isTextual()) {
                return LuaValue.valueOf(jsonNode.asText());
            }

            return LuaUtil.fromJson(jsonNode);
        } catch (IOException e) {
            LOGGER.warn(e.getMessage(), e);

            return LuaValue.valueOf(new String(messagePayload));
        }
    }

    private void clearTopic() {
        clear(Features.TOPIC);
    }

    private String getMessage() {
        return LuaUtil.stringifyRaw(get(Features.MESSAGE));
    }

    private void clearMessage() {
        clear(Features.MESSAGE);
    }

    private record Message(byte[] payload, Runnable acknowledgement) { }

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
