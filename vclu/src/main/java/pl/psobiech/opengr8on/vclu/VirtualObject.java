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
import java.util.HashMap;
import java.util.Map;

import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.vclu.lua.fn.LuaOneArgFunction;

public class VirtualObject implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualObject.class);

    protected final String name;

    protected final Map<Integer, LuaValue> featureValues = new HashMap<>();

    protected final Map<Integer, LuaOneArgFunction> features = new HashMap<>();

    protected final Map<Integer, LuaOneArgFunction> funcs = new HashMap<>();

    protected final Map<Integer, org.luaj.vm2.LuaFunction> events = new HashMap<>();

    public VirtualObject(String name) {
        this.name = name;
    }

    public LuaValue get(int index) {
        final LuaOneArgFunction luaFunction = features.get(index);
        if (luaFunction != null) {
            final LuaValue returnValue = luaFunction.call(LuaValue.NIL);
            featureValues.put(index, returnValue);

            return returnValue;
        }

        return featureValues.getOrDefault(index, LuaValue.NIL);
    }

    public void set(int index, LuaValue luaValue) {
        featureValues.put(index, luaValue);

        final LuaOneArgFunction luaFunction = features.get(index);
        if (luaFunction != null) {
            luaFunction.call(luaValue);
        }
    }

    public LuaValue execute(int index, LuaValue luaValue) {
        final LuaOneArgFunction luaFunction = funcs.get(index);
        if (luaFunction != null) {
            return luaFunction.call(luaValue);
        }

        LOGGER.warn("Not implemented: " + name + ":execute(" + index + ")");

        return LuaValue.NIL;
    }

    public void addEvent(int index, org.luaj.vm2.LuaFunction luaValue) {
        events.put(index, luaValue);
    }

    @Override
    public void close() {
        // NOP
    }
}
