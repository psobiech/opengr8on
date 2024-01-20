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

import java.io.Closeable;
import java.util.Hashtable;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.vclu.system.VirtualSystem;
import pl.psobiech.opengr8on.vclu.system.lua.fn.BaseLuaFunction;
import pl.psobiech.opengr8on.vclu.system.lua.fn.LuaOneArgFunction;
import pl.psobiech.opengr8on.vclu.system.lua.fn.LuaSupplier;
import pl.psobiech.opengr8on.vclu.system.lua.fn.LuaTwoArgFunction;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;

public class VirtualObject implements Closeable {
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    protected final VirtualSystem virtualSystem;

    protected final String name;

    private final Map<Integer, LuaValue> featureValues = new Hashtable<>();

    private final Map<Integer, BaseLuaFunction> featureFunctions = new Hashtable<>();

    private final Map<Integer, BaseLuaFunction> methodFunctions = new Hashtable<>();

    private final Map<Integer, LuaFunction> eventFunctions = new Hashtable<>();

    protected final ScheduledExecutorService scheduler;

    private final Class<? extends Enum<? extends IFeature>> featureClass;

    private final Class<? extends Enum<? extends IMethod>> methodClass;

    private final Class<? extends Enum<? extends IEvent>> eventClass;

    private final Map<IEvent, Future<?>> eventTriggerFuture = new Hashtable<>();

    public VirtualObject(VirtualSystem virtualSystem, String name) {
        this(
            virtualSystem,
            name,
            IFeature.EMPTY.class, IMethod.EMPTY.class, IEvent.EMPTY.class
        );
    }

    public VirtualObject(
        VirtualSystem virtualSystem,
        String name,
        Class<? extends Enum<? extends IFeature>> featureClass,
        Class<? extends Enum<? extends IMethod>> methodClass,
        Class<? extends Enum<? extends IEvent>> eventClass
    ) {
        this.virtualSystem = virtualSystem;
        this.name          = name;
        this.scheduler     = ThreadUtil.virtualScheduler(name);

        this.featureClass = featureClass;
        this.methodClass  = methodClass;
        this.eventClass   = eventClass;
    }

    public String getName() {
        return name;
    }

    /**
     * Method executed once
     */
    public void setup() {
        // NOP
    }

    /**
     * Method executed every some time
     */
    public void loop() {
        // NOP
    }

    @Override
    public void close() {
        ThreadUtil.closeQuietly(scheduler);
    }

    public void register(IFeature feature, LuaSupplier fn) {
        registerFeature(feature.index(), fn);
    }

    public void register(IFeature feature, LuaOneArgFunction fn) {
        registerFeature(feature.index(), fn);
    }

    private void registerFeature(int index, BaseLuaFunction fn) {
        final BaseLuaFunction proxyFn = (a) -> {
            final LuaValue returnValue = fn.invoke(a);

            LOGGER.debug(
                "{}.get({}) = {}",
                name,
                IFeature.byIndex(index, featureClass)
                        .map(Enum::name)
                        .orElseGet(() -> String.valueOf(index)),
                LuaUtil.stringify(returnValue)
            );

            return returnValue;
        };

        featureFunctions.put(index, proxyFn);

        setValue(index, proxyFn.invoke(LuaValue.NIL));
    }

    public LuaValue get(IFeature feature) {
        return get(feature.index());
    }

    public LuaValue get(int index) {
        final BaseLuaFunction luaFunction = featureFunctions.get(index);
        if (luaFunction == null) {
            return getValue(index);
        }

        final LuaValue returnValue = luaFunction.invoke(LuaValue.NIL);
        setValue(index, returnValue);

        return returnValue;
    }

    public LuaValue getValue(IFeature feature) {
        return getValue(feature.index());
    }

    public LuaValue getValue(int index) {
        return featureValues.getOrDefault(index, LuaValue.NIL);
    }

    public LuaValue clear(IFeature feature) {
        return featureValues.remove(feature.index());
    }

    public void set(IFeature feature, LuaValue luaValue) {
        set(feature.index(), luaValue);
    }

    public void set(int index, LuaValue luaValue) {
        final BaseLuaFunction luaFunction = featureFunctions.get(index);
        if (luaFunction == null) {
            setValue(index, luaValue);

            return;
        }

        setValue(index, luaFunction.invoke(luaValue));
    }

    public void setValue(IFeature feature, LuaValue luaValue) {
        setValue(feature.index(), luaValue);
    }

    public void setValue(int index, LuaValue luaValue) {
        LOGGER.debug(
            "{}.set({}, {})",
            name,
            IFeature.byIndex(index, featureClass)
                    .map(Enum::name)
                    .orElseGet(() -> String.valueOf(index)),
            LuaUtil.stringify(luaValue)
        );

        featureValues.put(index, luaValue);
    }

    public void register(IMethod feature, LuaSupplier fn) {
        registerMethod(feature.index(), fn);
    }

    public void register(IMethod feature, LuaOneArgFunction fn) {
        registerMethod(feature.index(), fn);
    }

    public void register(IMethod feature, LuaTwoArgFunction fn) {
        registerMethod(feature.index(), fn);
    }

    private void registerMethod(int index, BaseLuaFunction fn) {
        methodFunctions.put(index, fn);
    }

    public LuaValue execute(IMethod method, Varargs luaValue) {
        return execute(method.index(), luaValue);
    }

    public LuaValue execute(int index, Varargs args) {
        final BaseLuaFunction luaFunction = methodFunctions.get(index);
        if (luaFunction == null) {
            LOGGER.warn(
                "{}.execute({}, {}) -- NOT IMPLEMENTED",
                name,
                IMethod.byIndex(index, methodClass)
                       .map(Enum::name)
                       .orElseGet(() -> String.valueOf(index)),
                LuaUtil.stringifyRaw(args)
            );

            return LuaValue.NIL;
        }

        LOGGER.debug(
            "{}.execute({}, {})",
            name,
            IMethod.byIndex(index, methodClass)
                   .map(Enum::name)
                   .orElseGet(() -> String.valueOf(index)),
            LuaUtil.stringifyRaw(args)
        );

        return luaFunction.invoke(args);
    }

    public boolean triggerEvent(IEvent event) {
        return triggerEvent(event, null);
    }

    public boolean triggerEvent(IEvent event, Runnable onCompleted) {
        if (!isEventRegistered(event)) {
            LOGGER.trace("{}.triggerEvent({}) -- NOT REGISTERED", name, event.name());

            if (onCompleted != null) {
                scheduler.submit(onCompleted);
            }

            return false;
        }

        final LuaFunction luaFunction = eventFunctions.get(event.address());
        try {
            LOGGER.debug("{}.triggerEvent({})", name, event.name());

            awaitEventTrigger(event);
            eventTriggerFuture.put(
                event,
                scheduler.submit(() -> {
                    try {
                        luaFunction.call();
                    } finally {
                        if (onCompleted != null) {
                            scheduler.submit(onCompleted);
                        }
                    }
                })
            );

            return true;
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        return false;
    }

    public boolean isEventRegistered(IEvent event) {
        final int address = event.address();
        final LuaFunction luaFunction = eventFunctions.get(address);

        return luaFunction != null;
    }

    public void awaitEventTrigger(IEvent event) {
        ThreadUtil.await(eventTriggerFuture.remove(event));
    }

    public void addEventHandler(IEvent event, LuaFunction luaFunction) {
        addEventHandler(event.address(), luaFunction);
    }

    public void addEventHandler(int address, LuaFunction luaFunction) {
        eventFunctions.put(address, luaFunction);
    }

    public interface IFeature {
        int index();

        String name();

        static <E extends Enum<? extends IFeature>> Optional<E> byIndex(int index, Class<E> clazz) {
            for (E enumConstant : clazz.getEnumConstants()) {
                final IFeature feature = (IFeature) enumConstant;
                if (index == feature.index()) {
                    return Optional.of(enumConstant);
                }
            }

            return Optional.empty();
        }

        enum EMPTY implements IFeature {
            //
            ;

            @Override
            public int index() {
                return 0;
            }
        }
    }

    public interface IMethod {
        int index();

        String name();

        static <E extends Enum<? extends IMethod>> Optional<E> byIndex(int index, Class<E> clazz) {
            for (E enumConstant : clazz.getEnumConstants()) {
                final IMethod feature = (IMethod) enumConstant;
                if (index == feature.index()) {
                    return Optional.of(enumConstant);
                }
            }

            return Optional.empty();
        }

        enum EMPTY implements IMethod {
            //
            ;

            @Override
            public int index() {
                return 0;
            }
        }
    }

    public interface IEvent {
        int address();

        String name();

        static <E extends Enum<? extends IEvent>> Optional<E> byAddress(int address, Class<E> clazz) {
            for (E enumConstant : clazz.getEnumConstants()) {
                final IEvent feature = (IEvent) enumConstant;
                if (address == feature.address()) {
                    return Optional.of(enumConstant);
                }
            }

            return Optional.empty();
        }

        enum EMPTY implements IEvent {
            //
            ;

            @Override
            public int address() {
                return 0;
            }
        }
    }
}
