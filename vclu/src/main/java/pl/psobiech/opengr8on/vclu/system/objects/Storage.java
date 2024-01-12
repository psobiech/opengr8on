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

import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Storage extends VirtualObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(Storage.class);

    public static final int INDEX = 44;

    public Storage(String name) {
        super(name);

        register(Features.UNKNOWN, arg1 -> {
            return LuaValue.ZERO;
        });

        register(Methods.STORE, arg1 -> {
            final String persistentVariableName = arg1.checkjstring();

            // TODO: make variable persistent across restarts

            return LuaValue.NIL;
        });

        register(Methods.ERASE, arg1 -> {
            return LuaValue.NIL;
        });
    }

    private enum Features implements IFeature {
        UNKNOWN(1),
        //
        ;

        private final int index;

        Features(int index) {
            this.index = index;
        }

        @Override
        public int index() {
            return index;
        }
    }

    private enum Methods implements IMethod {
        ERASE(3),
        STORE(4),
        //
        ;

        private final int index;

        Methods(int index) {
            this.index = index;
        }

        @Override
        public int index() {
            return index;
        }
    }
}
