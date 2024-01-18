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

import java.io.Closeable;
import java.util.concurrent.locks.ReentrantLock;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UncheckedInterruptedException;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.vclu.system.VirtualSystem;

public class LuaThread implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(LuaThread.class);

    private final VirtualSystem virtualSystem;

    private final ReentrantLock globalsLock = new ReentrantLock();

    private final Globals globals;

    private final Thread thread;

    private final boolean emergency;

    public LuaThread(VirtualSystem virtualSystem, Globals globals, boolean emergency, LuaClosure mainLuaClosure) {
        this.thread = Thread.ofVirtual()
                            .name(getClass().getSimpleName())
                            .unstarted(
                                () -> {
                                    virtualSystem.setLuaThread(LuaThread.this);

                                    try {
                                        mainLuaClosure.call();
                                    } catch (LuaError e) {
                                        if (e.getCause() instanceof UncheckedInterruptedException) {
                                            LOGGER.trace(e.getMessage(), e);

                                            return;
                                        }

                                        LOGGER.error(e.getMessage(), e);
                                    } catch (Exception e) {
                                        LOGGER.error(e.getMessage(), e);
                                    }
                                }
                            );

        this.virtualSystem = virtualSystem;
        this.globals       = globals;
        this.emergency     = emergency;
    }

    public LuaValue luaCall(String script) {
        globalsLock.lock();
        try {
            return globals.load("return %s".formatted(script))
                          .call();
        } finally {
            globalsLock.unlock();
        }
    }

    public VirtualSystem virtualSystem() {
        return virtualSystem;
    }

    public void start() {
        thread.start();
    }

    public boolean isEmergency() {
        return emergency;
    }

    @Override
    public void close() {
        IOUtil.closeQuietly(virtualSystem);

        thread.interrupt();

        try {
            thread.join();
        } catch (InterruptedException e) {
            LOGGER.trace(e.getMessage(), e);
        }

        IOUtil.closeQuietly(globals.STDOUT, globals.STDERR);
    }
}
