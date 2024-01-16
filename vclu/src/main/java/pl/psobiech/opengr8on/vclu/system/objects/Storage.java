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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.vclu.system.lua.LuaThread;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;

public class Storage extends VirtualObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(Storage.class);

    public static final int INDEX = 44;

    private final LuaThread luaThread;

    private final Path storagePath;

    private final ReentrantLock variablesLock = new ReentrantLock();

    private final Map<String, LuaValue> variables = new HashMap<>();

    public Storage(String name, LuaThread luaThread, Path storageRootPath) {
        super(
            name,
            Features.class, Methods.class, IEvent.EMPTY.class
        );

        this.luaThread = luaThread;

        FileUtil.mkdir(storageRootPath);

        this.storagePath = storageRootPath.resolve("storage.json");

        register(Features.UNKNOWN, () -> LuaValue.ZERO);

        register(Methods.STORE, arg1 -> {
            final String variableName = arg1.checkjstring();

            variablesLock.lock();
            try {
                variables.put(variableName, LuaValue.NIL);
            } finally {
                variablesLock.unlock();
            }

            return LuaValue.NIL;
        });

        register(Methods.ERASE_ALL, arg1 -> {
            variablesLock.lock();
            try {
                final Set<String> variableNames = new HashSet<>(variables.keySet());
                for (String variableName : variableNames) {
                    variables.put(variableName, LuaValue.NIL);
                }

                FileUtil.deleteQuietly(storagePath);
            } finally {
                variablesLock.unlock();
            }

            return LuaValue.NIL;
        });
    }

    @Override
    public void setup() {
        restore();

        executor.scheduleAtFixedRate(this::updateAndStore, 1, 1, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private void restore() {
        if (!Files.exists(storagePath)) {
            return;
        }

        variablesLock.lock();
        try {
            final Map<String, Object> storedVariables = ObjectMapperFactory.JSON.readValue(storagePath.toFile(), HashMap.class);
            for (Entry<String, Object> entry : storedVariables.entrySet()) {
                variables.put(entry.getKey(), LuaUtil.fromObject(entry.getValue()));
            }
        } catch (IOException e) {
            throw new UnexpectedException(e);
        } finally {
            variablesLock.unlock();
        }
    }

    private void updateAndStore() {
        boolean changed = false;
        final Map<String, Object> storedVariables = new HashMap<>();

        variablesLock.lock();
        try {
            final Set<String> variableNames = new HashSet<>(variables.keySet());
            for (String variableName : variableNames) {
                final LuaValue value = luaThread.luaCall(variableName);
                if (!value.equals(variables.put(variableName, value))) {
                    changed = true;
                }

                variables.put(variableName, value);
                storedVariables.put(variableName, LuaUtil.asObject(value));
            }
        } finally {
            variablesLock.unlock();
        }

        if (changed) {
            try {
                ObjectMapperFactory.JSON.writeValue(storagePath.toFile(), storedVariables);
            } catch (IOException e) {
                throw new UnexpectedException(e);
            }
        }
    }

    private void store() {
        variablesLock.lock();
        try {
            final Map<String, Object> storedVariables = new HashMap<>();
            for (Entry<String, LuaValue> entry : variables.entrySet()) {
                final String variableName = entry.getKey();

                storedVariables.put(variableName, LuaUtil.asObject(entry.getValue()));
            }

            ObjectMapperFactory.JSON.writeValue(storagePath.toFile(), storedVariables);
        } catch (IOException e) {
            throw new UnexpectedException(e);
        } finally {
            variablesLock.unlock();
        }
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
        ERASE_ALL(3),
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
