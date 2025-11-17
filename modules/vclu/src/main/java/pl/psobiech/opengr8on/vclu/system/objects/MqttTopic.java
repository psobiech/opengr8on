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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.util.Util;
import pl.psobiech.opengr8on.vclu.MqttClient;
import pl.psobiech.opengr8on.vclu.system.VirtualSystem;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

public class MqttTopic extends VirtualObject {
    public static final int INDEX = 999;

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttTopic.class);

    private final Map<String, MqttTopicFilter> topicFilters = new ConcurrentHashMap<>();

    private final LinkedBlockingDeque<Map.Entry<String, Message>> messageQueue = new LinkedBlockingDeque<>();

    private final MqttClient mqttClient;

    public MqttTopic(VirtualSystem virtualSystem, String name) {
        super(
                virtualSystem, name,
                Features.class, Methods.class, Events.class
        );

        this.mqttClient = virtualSystem.getVirtualClu()
                                       .getMqttClient();

        register(Methods.SUBSCRIBE, this::subscribe);
        register(Methods.UNSUBSCRIBE, this::unsubscribe);
        register(Methods.PUBLISH, this::publish);
    }

    private static byte[] messageAsPayload(LuaValue luaValue) throws JsonProcessingException {
        if (luaValue.istable()) {
            return ObjectMapperFactory.JSON.writeValueAsBytes(LuaUtil.asObject(luaValue));
        }

        return luaValue.checkjstring()
                       .getBytes(StandardCharsets.UTF_8);
    }

    private static LuaValue messageFromPayload(Message message) {
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

    @Override
    public void setup() {
        final VirtualCLU virtualClu = virtualSystem.getVirtualClu();
        if (virtualClu == null) {
            return;
        }

        triggerEvent(Events.INIT);
    }

    private LuaValue subscribe(LuaValue arg1) {
        if (!isMqttInitialized()) {
            return LuaValue.FALSE;
        }

        try {
            final String topic = arg1.checkjstring();

            topicFilters.put(topic, MqttTopicFilter.of(topic));
            mqttClient.subscribeWithManualAck(topic, (bytes, ack) -> onMessage(topic, bytes, ack));

            return LuaValue.TRUE;
        } catch (RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return LuaValue.FALSE;
    }

    private LuaValue unsubscribe(LuaValue arg1) {
        if (!isMqttInitialized()) {
            return LuaValue.FALSE;
        }

        try {
            final String topic = arg1.checkjstring();

            topicFilters.remove(topic);
            mqttClient.unsubscribe(topic);

            return LuaValue.TRUE;
        } catch (RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return LuaValue.FALSE;
    }

    private LuaValue publish(LuaValue topicArg, LuaValue messageArg) {
        if (!isMqttInitialized()) {
            return LuaValue.FALSE;
        }

        final String topic = LuaUtil.isNil(topicArg) ? getTopic() : topicArg.checkjstring();
        if (isSubscribedTo(topic)) {
            LOGGER.warn("Attempt to publish to a topic that we are subscribed to ({}) was ignored (to prevent infinite message bounce)", topic);

            return LuaValue.FALSE;
        }

        try {
            final byte[] payload = messageAsPayload(LuaUtil.isNil(messageArg) ? get(Features.MESSAGE) : messageArg);

            mqttClient.publish(topic, payload);

            return LuaValue.TRUE;
        } catch (IOException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return LuaValue.FALSE;
    }

    private boolean isMqttInitialized() {
        if (mqttClient.isInitialized()) {
            return true;
        }

        final UnexpectedException exception = new UnexpectedException("MQTT is not yet initialized..");
        LOGGER.warn(exception.getMessage(), exception);

        return false;
    }

    public void onMessage(String topic, byte[] payload, Runnable acknowledged) {
        if (!isSubscribedTo(topic)) {
            return;
        }

        while (!messageQueue.offer(Map.entry(topic, new Message(payload, acknowledged)))) {
            // TODO: retry/fail logic
            Util.yield();
        }
    }

    private boolean isSubscribedTo(String topic) {
        for (MqttTopicFilter topicFilter : topicFilters.values()) {
            if (topicFilter.matches(com.hivemq.client.mqtt.datatypes.MqttTopic.of(topic))) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void loop() {
        final String currentPayload = getMessage();
        if (currentPayload == null || currentPayload.isEmpty()) {
            Entry<String, Message> entry;
            while ((entry = messageQueue.poll()) != null) {
                final String topic = entry.getKey();
                final Message message = entry.getValue();

                awaitEventTrigger(Events.MESSAGE);
                set(Features.TOPIC, LuaValue.valueOf(topic));
                set(Features.MESSAGE, messageFromPayload(message));
                if (triggerEvent(Events.MESSAGE, this::clearMessage)) {
                    message.acknowledgement()
                           .run();
                }

                Util.yield();
            }
        }
    }

    private void clearTopic() {
        clear(Features.TOPIC);
    }

    private String getTopic() {
        return LuaUtil.stringifyRaw(get(Features.TOPIC));
    }

    private String getMessage() {
        return LuaUtil.stringifyRaw(get(Features.MESSAGE));
    }

    private void clearMessage() {
        clear(Features.MESSAGE);
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

    private record Message(byte[] payload, Runnable acknowledgement) {
    }
}
