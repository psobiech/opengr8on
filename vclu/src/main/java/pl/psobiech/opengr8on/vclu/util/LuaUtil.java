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

import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;

public class LuaUtil {
    private LuaUtil() {
        // NOP
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
        if (luaValue == null || luaValue.isnil()) {
            return "nil";
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
