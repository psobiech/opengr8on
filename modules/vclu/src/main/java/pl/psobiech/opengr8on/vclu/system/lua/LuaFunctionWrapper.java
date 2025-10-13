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

package pl.psobiech.opengr8on.vclu.system.lua;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.LibFunction;
import org.slf4j.Logger;
import pl.psobiech.opengr8on.exceptions.UncheckedInterruptedException;
import pl.psobiech.opengr8on.vclu.system.lua.fn.*;

/**
 * Function wrapper that logs any exceptions that otherwise would be swallowed in the LUA thread
 */
public class LuaFunctionWrapper extends LibFunction {
    private final Logger logger;

    private final BaseLuaFunction fn;

    LuaFunctionWrapper(Logger logger, BaseLuaFunction fn) {
        this.logger = logger;
        this.fn = fn;
    }

    public static LibFunction wrap(Logger logger, LuaNoArgConsumer luaFunction) {
        return wrap(logger, (BaseLuaFunction) luaFunction);
    }

    public static LibFunction wrap(Logger logger, LuaThreeArgConsumer luaFunction) {
        return wrap(logger, (BaseLuaFunction) luaFunction);
    }

    public static LibFunction wrap(Logger logger, LuaVarArgConsumer luaFunction) {
        return wrap(logger, (BaseLuaFunction) luaFunction);
    }

    public static LibFunction wrap(Logger logger, LuaVarArgFunction luaFunction) {
        return wrap(logger, (BaseLuaFunction) luaFunction);
    }

    public static LibFunction wrap(Logger logger, LuaTwoArgFunction luaFunction) {
        return wrap(logger, (BaseLuaFunction) luaFunction);
    }

    public static LibFunction wrap(Logger logger, LuaThreeArgFunction luaFunction) {
        return wrap(logger, (BaseLuaFunction) luaFunction);
    }

    private static LibFunction wrap(Logger logger, BaseLuaFunction luaFunction) {
        return new LuaFunctionWrapper(logger, luaFunction);
    }

    @Override
    public LuaValue call() {
        return invoke(LuaValue.NIL);
    }

    @Override
    public LuaValue call(LuaValue a) {
        return invoke(a);
    }

    @Override
    public LuaValue call(LuaValue a, LuaValue b) {
        return invoke(LuaValue.varargsOf(a, b));
    }

    @Override
    public LuaValue call(LuaValue a, LuaValue b, LuaValue c) {
        return invoke(LuaValue.varargsOf(a, b, c));
    }

    @Override
    public LuaValue call(LuaValue a, LuaValue b, LuaValue c, LuaValue d) {
        final LuaValue[] luaValues = {a, b, c, d};

        return invoke(LuaValue.varargsOf(luaValues, 0, luaValues.length));
    }

    @Override
    public LuaValue invoke(Varargs args) {
        try {
            return fn.invoke(args);
        } catch (UncheckedInterruptedException e) {
            logger.trace(e.getMessage(), e);

            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);

            throw e;
        }
    }
}
