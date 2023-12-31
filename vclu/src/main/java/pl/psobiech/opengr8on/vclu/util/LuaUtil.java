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

package pl.psobiech.opengr8on.vclu.util;

import java.util.HashMap;
import java.util.Map;

import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public class LuaUtil {
    private LuaUtil() {
        // NOP
    }

    public static Map<String, String> tableStringString(LuaValue luaValue) {
        if (luaValue == null || luaValue.isnil()) {
            return Map.of();
        }

        if (luaValue.isstring() && luaValue.checkjstring().isEmpty()) {
            return Map.of();
        }

        final Map<String, String> map = new HashMap<>();
        final LuaTable table = luaValue.checktable();
        for (LuaValue key : table.keys()) {
            final LuaValue value = table.get(key);

            map.put(key.checkjstring(), value.checkjstring());
        }

        return map;
    }

    public static Map<LuaValue, LuaValue> table(LuaValue object) {
        final Map<LuaValue, LuaValue> map = new HashMap<>();

        final LuaTable table = object.checktable();
        for (LuaValue key : table.keys()) {
            final LuaValue value = table.get(key);

            map.put(key, value);
        }

        return map;
    }

    public static boolean trueish(LuaValue luaValue) {
        if (luaValue == null || luaValue.isnil()) {
            return false;
        }

        return (luaValue.isboolean() && luaValue.checkboolean())
            || (luaValue.isnumber() && luaValue.checklong() != 0)
            || (luaValue.isstring() && Boolean.parseBoolean(luaValue.checkjstring()));
    }

    public static String toString(LuaValue luaValue) {
        if (luaValue == null || luaValue.isnil()) {
            return "nil";
        }

        if (luaValue.isstring()) {
            return "\"" + luaValue.checkjstring() + "\"";
        }

        return stringify(luaValue);
    }

    public static String stringify(LuaValue luaValue) {
        return stringify(luaValue, null);
    }

    public static String stringify(LuaValue luaValue, String nilValue) {
        if (luaValue == null || luaValue.isnil()) {
            return nilValue;
        }

        if (luaValue instanceof LuaString) {
            return luaValue.checkjstring();
        }

        if (luaValue.isnumber()) {
            return String.valueOf(luaValue.checknumber());
        }

        if (luaValue.isstring()) {
            return luaValue.checkjstring();
        }

        if (luaValue.isboolean()) {
            return String.valueOf(luaValue.checkboolean());
        }

        return String.valueOf(luaValue);
    }
}
