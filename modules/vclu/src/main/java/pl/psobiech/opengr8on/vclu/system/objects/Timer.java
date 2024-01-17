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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.luaj.vm2.LuaValue;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;

public class Timer extends VirtualObject {
    public static final int INDEX = 6;

    private final AtomicLong counter = new AtomicLong(0);

    private volatile long lastLoopTime;

    private volatile State state = State.STOPPED;

    public Timer(String name) {
        super(
            name,
            Features.class, Methods.class, Events.class
        );

        register(Features.TIME, (arg1) -> {
            if (LuaUtil.isNil(arg1)) {
                return getValue(Features.TIME);
            }

            counter.set(TimeUnit.MILLISECONDS.toNanos(arg1.checklong()));

            return arg1;
        });
        set(Features.MODE, LuaValue.ZERO);

        register(Features.STATE, () -> LuaValue.valueOf(state.ordinal()));
        register(Features.VALUE, () -> LuaValue.valueOf(TimeUnit.NANOSECONDS.toMillis(counter.get())));

        register(Methods.START, this::onStart);
        register(Methods.STOP, this::onStop);
        register(Methods.PAUSE, this::onPause);
    }

    private LuaValue onStart() {
        counter.set(TimeUnit.MILLISECONDS.toNanos(get(Features.TIME).checklong()));

        lastLoopTime = System.nanoTime();
        state        = State.COUNTING;

        triggerEvent(Events.START);

        return LuaValue.NIL;
    }

    private LuaValue onStop() {
        counter.set(TimeUnit.MILLISECONDS.toNanos(get(Features.TIME).checklong()));

        state = State.STOPPED;

        triggerEvent(Events.STOP);

        return LuaValue.NIL;
    }

    private LuaValue onPause() {
        if (state == State.PAUSED) {
            lastLoopTime = System.nanoTime();
            state        = State.COUNTING;
        } else if (state == State.COUNTING) {
            state = State.PAUSED;

            triggerEvent(Events.PAUSE);
        }

        return LuaValue.NIL;
    }

    @Override
    public void loop() {
        if (state != State.COUNTING) {
            return;
        }

        final long now = System.nanoTime();
        final long delta = now - lastLoopTime;
        lastLoopTime = now;

        final long value = counter.addAndGet(-delta);
        if (value <= 0) {
            if (get(Features.MODE).checkint() == Mode.INTERVAL.mode()) {
                counter.addAndGet(TimeUnit.MILLISECONDS.toNanos(get(Features.TIME).checklong()));
            } else {
                onStop();
            }

            triggerEvent(Events.TIMER);
        }
    }

    private enum Mode {
        COUNT_DOWN(0),
        INTERVAL(1),
        //
        ;

        private final int mode;

        Mode(int mode) {
            this.mode = mode;
        }

        public int mode() {
            return mode;
        }
    }

    private enum State {
        STOPPED,
        COUNTING,
        PAUSED,
        //
        ;
    }

    private enum Features implements IFeature {
        TIME(0),
        MODE(1),
        STATE(2),
        VALUE(3),
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
        START(0),
        STOP(1),
        PAUSE(2),
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
        TIMER(0),
        START(1),
        STOP(2),
        PAUSE(3),
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
