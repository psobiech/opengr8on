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

import java.util.ArrayList;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.LibFunction;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.vclu.VirtualSystem.Subscription;

public class CluLuaApi extends TwoArgFunction {
    private static final Logger LOGGER = LoggerFactory.getLogger(CluLuaApi.class);

    private final VirtualSystem virtualSystem;

    private final Globals globals;

    public CluLuaApi(VirtualSystem virtualSystem, Globals globals) {
        this.virtualSystem = virtualSystem;
        this.globals = globals;
    }

    public LuaValue call(LuaValue moduleName, LuaValue environment) {
        final LuaValue library = tableOf();

        library.set("setup", new LibFunction() {
            @Override
            public LuaValue call() {
                virtualSystem.setup();

                return LuaValue.NIL;
            }
        });

        library.set("loop", new LibFunction() {
            @Override
            public LuaValue call() {
                virtualSystem.loop();

                return LuaValue.NIL;
            }
        });

        library.set("sleep", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                virtualSystem.sleep(arg.checklong());

                return LuaValue.NIL;
            }
        });

        library.set("logDebug", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                LOGGER.debug(String.valueOf(arg.checkstring()));

                return LuaValue.NIL;
            }
        });

        library.set("logInfo", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                LOGGER.info(String.valueOf(arg.checkstring()));

                return LuaValue.NIL;
            }
        });

        library.set("logWarning", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                LOGGER.warn(String.valueOf(arg.checkstring()));

                return LuaValue.NIL;
            }
        });

        library.set("logError", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                LOGGER.error(String.valueOf(arg.checkstring()));

                return LuaValue.NIL;
            }
        });

        library.set("clientRegister", new LibFunction() {
            @Override
            public LuaValue invoke(Varargs args) {
                final LuaValue object = args.arg(4);

                final ArrayList<Subscription> subscriptions = new ArrayList<>();
                if (!object.istable()) {
                    return LuaValue.valueOf("values:{" + 99 + "}");
                }
                final LuaTable checktable = object.checktable();
                for (LuaValue key : checktable.keys()) {
                    final LuaValue keyValue = checktable.get(key);
                    if (!keyValue.istable()) {
                        return LuaValue.valueOf("values:{" + 99 + "}");
                    }

                    subscriptions.add(
                        new Subscription(
                            String.valueOf(keyValue.checktable().get(1).checktable().get("name")),
                            keyValue.checktable().get(2).checkint()
                        )
                    );
                }

                return valueOf(
                    virtualSystem.clientRegister(
                        String.valueOf(args.arg1().checkstring()),
                        args.arg(2).checkint(),
                        args.arg(3).checkint(),
                        subscriptions
                    )
                );
            }
        });

        library.set("clientDestroy", new LibFunction() {
            @Override
            public LuaValue invoke(Varargs args) {
                return virtualSystem.clientDestroy(
                    String.valueOf(args.arg1().checkstring()),
                    args.arg(2).checkint(),
                    args.arg(3).checkint()
                );
            }
        });

        library.set("fetchValues", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue object) {
                final ArrayList<Subscription> subscriptions = new ArrayList<>();
                if (!object.istable()) {
                    return LuaValue.valueOf("values:{" + 99 + "}");
                }

                final LuaTable checktable = object.checktable();
                for (LuaValue key : checktable.keys()) {
                    final LuaValue keyValue = checktable.get(key);
                    if (!keyValue.istable()) {
                        return valueOf("values:{\"" + globals.load("return _G[\"%s\"]".formatted(keyValue)).call() + "\"}");
                    }

                    subscriptions.add(
                        new Subscription(
                            String.valueOf(keyValue.checktable().get(1).checktable().get("name")),
                            keyValue.checktable().get(2).checkint()
                        )
                    );
                }

                return valueOf(
                    "values:" + virtualSystem.fetchValues(
                        subscriptions
                    )
                );
            }

            @Override
            public LuaValue call(LuaValue object, LuaValue arg1, LuaValue arg2) {
                virtualSystem.getObject(object.checkint()).addEvent(arg1.checkint(), arg2.checkfunction());

                return LuaValue.NIL;
            }
        });

        library.set("newObject", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue arg1, LuaValue arg2, LuaValue arg3) {
                return valueOf(
                    virtualSystem.newObject(arg1.checkint(), String.valueOf(arg2.checkstring()), arg3.checkint())
                );
            }
        });

        library.set("newGate", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue arg1, LuaValue arg2, LuaValue arg3) {
                return valueOf(
                    virtualSystem.newGate(arg1.checkint(), String.valueOf(arg2.checkstring()))
                );
            }
        });

        library.set("get", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue object, LuaValue arg) {
                return virtualSystem.getObject(object.checkint()).get(arg.checkint());
            }
        });

        library.set("set", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue object, LuaValue arg1, LuaValue arg2) {
                virtualSystem.getObject(object.checkint()).set(arg1.checkint(), arg2);

                return LuaValue.NIL;
            }
        });

        library.set("execute", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue object, LuaValue arg1, LuaValue arg2) {
                return virtualSystem.getObject(object.checkint()).execute(arg1.checkint(), arg2);
            }
        });

        library.set("addEvent", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue object, LuaValue arg1, LuaValue arg2) {
                virtualSystem.getObject(object.checkint()).addEvent(arg1.checkint(), arg2.checkfunction());

                return LuaValue.NIL;
            }
        });

        environment.set("api", library);

        return library;
    }
}
