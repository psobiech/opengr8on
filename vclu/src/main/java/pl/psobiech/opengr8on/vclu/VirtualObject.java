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

import java.io.Closeable;
import java.util.Hashtable;
import java.util.Map;

import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.vclu.lua.fn.LuaOneArgFunction;

public class VirtualObject implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualObject.class);

    protected final String name;

    private final Map<Integer, LuaValue> featureValues = new Hashtable<>();

    private final Map<Integer, LuaOneArgFunction> featureFunctions = new Hashtable<>();

    private final Map<Integer, LuaOneArgFunction> methodFunctions = new Hashtable<>();

    private final Map<Integer, org.luaj.vm2.LuaFunction> eventFunctions = new Hashtable<>();

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
        int index = feature.index();
        featureFunctions.put(index, fn);
    }

    public LuaValue get(IFeature feature) {
        return get(feature.index());
    }

    public LuaValue get(int index) {
        final LuaOneArgFunction luaFunction = featureFunctions.get(index);
        if (luaFunction == null) {
            return featureValues.getOrDefault(index, LuaValue.NIL);
        }

        final LuaValue returnValue = luaFunction.call(LuaValue.NIL);
        featureValues.put(index, returnValue);

        return returnValue;
    }

    public LuaValue getValue(IFeature feature) {
        return featureValues.getOrDefault(feature.index(), LuaValue.NIL);
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
            featureValues.put(index, luaValue);

            return;
        }

        featureValues.put(
            index,
            luaFunction.call(luaValue)
        );
    }

    public void register(IMethod feature, LuaOneArgFunction fn) {
        methodFunctions.put(feature.index(), fn);
    }

    public LuaValue execute(IMethod method, LuaValue luaValue) {
        return execute(method.index(), luaValue);
    }

    public LuaValue execute(int index, LuaValue luaValue) {
        final LuaOneArgFunction luaFunction = methodFunctions.get(index);
        if (luaFunction == null) {
            LOGGER.warn("Not implemented: " + name + ":execute(" + index + ")");

            return LuaValue.NIL;
        }

        return luaFunction.call(luaValue);
    }

    public void triggerEvent(IEvent event) {
        int address = event.address();
        final LuaFunction luaFunction = eventFunctions.get(address);
        if (luaFunction == null) {
            LOGGER.warn("Not implemented: " + name + ":addEvent(" + address + ")");

            return;
        }

        try {
            luaFunction.call();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public void addEventHandler(IEvent event, org.luaj.vm2.LuaFunction luaFunction) {
        eventFunctions.put(event.address(), luaFunction);
    }

    public void addEventHandler(int address, org.luaj.vm2.LuaFunction luaFunction) {
        eventFunctions.put(address, luaFunction);
    }

    @Override
    public void close() {
        // NOP
    }

    public interface IFeature {
        int index();
    }

    public interface IMethod {
        int index();
    }

    public interface IEvent {
        int address();
    }
}
