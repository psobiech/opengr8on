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

import java.io.Closeable;
import java.util.Hashtable;
import java.util.Map;
import java.util.Optional;

import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.vclu.lua.fn.BaseLuaFunction;
import pl.psobiech.opengr8on.vclu.lua.fn.LuaOneArgFunction;
import pl.psobiech.opengr8on.vclu.lua.fn.LuaTwoArgFunction;

public class VirtualObject implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualObject.class);

    protected final String name;

    private final Map<Integer, LuaValue> featureValues = new Hashtable<>();

    private final Map<Integer, LuaOneArgFunction> featureFunctions = new Hashtable<>();

    private final Map<Integer, BaseLuaFunction> methodFunctions = new Hashtable<>();

    private final Map<Integer, LuaFunction> eventFunctions = new Hashtable<>();

    public VirtualObject(String name) {
        this.name = name;
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

    public void register(IFeature feature, LuaOneArgFunction fn) {
        final int index = feature.index();
        featureFunctions.put(index, fn);
    }

    public LuaValue get(IFeature feature) {
        return get(feature.index());
    }

    public LuaValue get(int index) {
        final LuaOneArgFunction luaFunction = featureFunctions.get(index);
        if (luaFunction == null) {
            return getValue(index);
        }

        final LuaValue returnValue = luaFunction.call(LuaValue.NIL);
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
        final LuaOneArgFunction luaFunction = featureFunctions.get(index);
        if (luaFunction == null) {
            setValue(index, luaValue);

            return;
        }

        setValue(index, luaFunction.call(luaValue));
    }

    public void setValue(IFeature feature, LuaValue luaValue) {
        setValue(feature.index(), luaValue);
    }

    public void setValue(int index, LuaValue luaValue) {
        featureValues.put(index, luaValue);
    }

    public void register(IMethod feature, LuaOneArgFunction fn) {
        methodFunctions.put(feature.index(), fn);
    }

    public void register(IMethod feature, LuaTwoArgFunction fn) {
        methodFunctions.put(feature.index(), fn);
    }

    public LuaValue execute(IMethod method, Varargs luaValue) {
        return execute(method.index(), luaValue);
    }

    public LuaValue execute(int index, Varargs args) {
        final BaseLuaFunction luaFunction = methodFunctions.get(index);
        if (luaFunction == null) {
            LOGGER.warn("Not implemented: " + name + ":execute(" + index + ")");

            return LuaValue.NIL;
        }

        return luaFunction.invoke(args);
    }

    public void triggerEvent(IEvent event) {
        final int address = event.address();
        final LuaFunction luaFunction = eventFunctions.get(address);
        if (luaFunction == null) {
            LOGGER.warn("Not implemented: " + name + ":addEvent(" + address + ")");

            return;
        }

        try {
            Thread.yield();

            luaFunction.call();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public void addEventHandler(IEvent event, LuaFunction luaFunction) {
        eventFunctions.put(event.address(), luaFunction);
    }

    public void addEventHandler(int address, LuaFunction luaFunction) {
        eventFunctions.put(address, luaFunction);
    }

    @Override
    public void close() {
        // NOP
    }

    public interface IFeature {
        int index();

        static <E extends Enum<? extends IFeature>> Optional<E> byIndex(int index, Class<E> clazz) {
            for (E enumConstant : clazz.getEnumConstants()) {
                final IFeature feature = (IFeature) enumConstant;
                if (index == feature.index()) {
                    return Optional.of(enumConstant);
                }
            }

            return Optional.empty();
        }
    }

    public interface IMethod {
        int index();

        static <E extends Enum<? extends IMethod>> Optional<E> byIndex(int index, Class<E> clazz) {
            for (E enumConstant : clazz.getEnumConstants()) {
                final IMethod feature = (IMethod) enumConstant;
                if (index == feature.index()) {
                    return Optional.of(enumConstant);
                }
            }

            return Optional.empty();
        }
    }

    public interface IEvent {
        int address();

        static <E extends Enum<? extends IEvent>> Optional<E> byAddress(int address, Class<E> clazz) {
            for (E enumConstant : clazz.getEnumConstants()) {
                final IEvent feature = (IEvent) enumConstant;
                if (address == feature.address()) {
                    return Optional.of(enumConstant);
                }
            }

            return Optional.empty();
        }
    }
}
