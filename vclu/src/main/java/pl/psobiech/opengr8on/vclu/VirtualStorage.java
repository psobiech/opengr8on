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

import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VirtualStorage extends VirtualObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualStorage.class);

    public VirtualStorage(String name) {
        super(name);

        methodFunctions.put(4, arg1 -> {
            final LuaString persistentVariableName = arg1.checkstring();

            //TODO: make variable persistent across restarts

            return LuaValue.NIL;
        });
    }
}