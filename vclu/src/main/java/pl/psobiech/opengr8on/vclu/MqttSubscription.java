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

import java.util.concurrent.LinkedBlockingDeque;

import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MqttSubscription extends VirtualObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(MqttSubscription.class);

    private final LinkedBlockingDeque<byte[]> messageQueue = new LinkedBlockingDeque<>();

    public MqttSubscription(String name) {
        super(name);

        methodFunctions.put(0, this::onNextMessage); // mqttsubscription_nextmessage
    }

    private LuaValue onNextMessage(LuaValue arg1) {
        featureValues.remove(1);

        return LuaValue.NIL;
    }

    public String getTopic() {
        return String.valueOf(featureValues.get(0).checkstring());
    }

    public void enqueueMessage(int id, byte[] payload) {
        while (!messageQueue.offer(payload)) {
            // TODO: retry/fail logic
            Thread.yield();
        }
    }

    @Override
    public void loop() {
        final LuaValue currentPayload = featureValues.get(1); // mqttsubscription_message
        if (currentPayload == null) {
            final byte[] payload = messageQueue.poll();
            if (payload != null) {
                featureValues.put(1, LuaValue.valueOf(new String(payload)));

                triggerEvent(0); // mqttsubscription_onmessage
            }
        }
    }

    @Override
    public void close() {
        // NOP
    }
}
