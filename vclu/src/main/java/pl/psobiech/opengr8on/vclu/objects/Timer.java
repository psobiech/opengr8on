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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.vclu.VirtualObject;

public class Timer extends VirtualObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(Timer.class);

    private final AtomicLong counter = new AtomicLong(0);

    private volatile long lastLoopTime;

    private volatile State state = State.STOPPED;

    public Timer(String name) {
        super(name);

        featureValues.put(0, LuaValue.valueOf(0)); // timer_time_1
        featureValues.put(1, LuaValue.valueOf(0)); // timer_mode_1
        featureFunctions.put(2, arg1 -> LuaValue.valueOf(state.ordinal())); // timer_state_1
        featureFunctions.put(3, arg1 -> LuaValue.valueOf(TimeUnit.NANOSECONDS.toMillis(counter.get()))); // timer_value

        methodFunctions.put(0, this::onStart); // timer_start
        methodFunctions.put(1, this::onStop); // timer_stop
        methodFunctions.put(2, this::onPause); // timer_pause
    }

    @Override
    public void setup() {

    }

    private LuaValue onStart(LuaValue luaValue) {
        if (state != State.PAUSED) {
            counter.set(TimeUnit.MILLISECONDS.toNanos(featureValues.get(0).checklong()));
        }

        lastLoopTime = System.nanoTime();

        state = State.COUNTING;

        triggerEvent(1);

        return LuaValue.NIL;
    }

    private LuaValue onStop(LuaValue luaValue) {
        state = State.STOPPED;

        triggerEvent(2);

        return LuaValue.NIL;
    }

    private LuaValue onPause(LuaValue luaValue) {
        state = State.PAUSED;

        triggerEvent(3);

        return LuaValue.NIL;
    }

    @Override
    public void loop() {
        if (state != State.COUNTING) {
            return;
        }

        final long now = System.nanoTime();
        long delta = now - lastLoopTime;

        final long value = counter.addAndGet(-delta);
        if (value <= 0) {
            if (featureValues.get(1).checkint() == 1) {
                counter.set(TimeUnit.MILLISECONDS.toNanos(featureValues.get(0).checklong()));
            } else {
                counter.set(0);
                onStop(LuaValue.NIL);
            }

            triggerEvent(0);
        }

        lastLoopTime = now;
    }

    @Override
    public void close() {
        // NOP
    }

    private enum State {
        STOPPED,
        COUNTING,
        PAUSED,
        //
        ;
    }
}
