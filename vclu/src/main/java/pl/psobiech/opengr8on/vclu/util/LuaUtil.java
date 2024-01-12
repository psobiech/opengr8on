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

package pl.psobiech.opengr8on.vclu.util;

import java.util.HashMap;
import java.util.Map;

import org.luaj.vm2.LuaBoolean;
import org.luaj.vm2.LuaNumber;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public class LuaUtil {
    private static final String NIL_AS_STRING = "nil";

    private LuaUtil() {
        // NOP
    }

    public static Map<String, String> tableStringString(LuaValue luaValue) {
        if (isNil(luaValue)) {
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

    /**
     * @return true, if the luaValue is true, != 0, "true"
     */
    public static boolean trueish(LuaValue luaValue) {
        if (isNil(luaValue)) {
            return false;
        }

        return (luaValue.isboolean() && luaValue.checkboolean())
               || (luaValue.isnumber() && luaValue.checklong() != 0)
               || (luaValue.isstring() && Boolean.parseBoolean(luaValue.checkjstring()));
    }

    /**
     * @return luaValue converted to String, with String quoted or nil
     */
    public static String stringify(LuaValue luaValue) {
        if (isNil(luaValue)) {
            return NIL_AS_STRING;
        }

        if (luaValue instanceof LuaNumber) {
            return String.valueOf(luaValue.checknumber());
        }

        if (luaValue instanceof LuaBoolean) {
            return String.valueOf(luaValue.checkboolean());
        }

        if (luaValue.isstring()) {
            return "\"" + luaValue.checkjstring() + "\"";
        }

        return String.valueOf(luaValue);
    }

    public static boolean isNil(LuaValue luaValue) {
        return luaValue == null || luaValue.isnil();
    }

    /**
     * @return luaValue converted to String, or null
     */
    public static String stringifyRaw(LuaValue luaValue) {
        return stringifyRaw(luaValue, null);
    }

    /**
     * @return luaValue converted to String, or nilValue
     */
    public static String stringifyRaw(LuaValue luaValue, String nilValue) {
        if (isNil(luaValue)) {
            return nilValue;
        }

        if (luaValue instanceof LuaNumber) {
            return String.valueOf(luaValue.checknumber());
        }

        if (luaValue instanceof LuaBoolean) {
            return String.valueOf(luaValue.checkboolean());
        }

        if (luaValue.isstring()) {
            return luaValue.checkjstring();
        }

        return String.valueOf(luaValue);
    }
}
