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

package pl.psobiech.opengr8on.vclu.system.lua.fn;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public interface LuaThreeArgConsumer extends BaseLuaFunction {
    @Override
    default LuaValue invoke(Varargs args) {
        final LuaValue firstArg = args.arg(1);
        final LuaValue secondArg = args.arg(2);
        final LuaValue thirdArg = args.arg(3);

        call(firstArg, secondArg, thirdArg);

        return LuaValue.NIL;
    }

    void call(LuaValue arg1, LuaValue arg2, LuaValue arg3);
}
