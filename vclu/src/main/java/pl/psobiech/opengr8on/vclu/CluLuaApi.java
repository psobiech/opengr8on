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

        library.set(
            "setup",
            LuaServer.wrap(LOGGER, () -> {
                virtualSystem.setup();

                return LuaValue.NIL;
            })
        );

        library.set(
            "loop",
            LuaServer.wrap(LOGGER, () -> {
                virtualSystem.loop();

                return LuaValue.NIL;
            })
        );

        library.set(
            "sleep",
            LuaServer.wrap(LOGGER, args -> {
                virtualSystem.sleep(args.checklong(1));

                return LuaValue.NIL;
            })
        );

        library.set(
            "logDebug",
            LuaServer.wrap(LOGGER, args -> {
                LOGGER.debug(String.valueOf(args.checkstring(1)));

                return LuaValue.NIL;
            })
        );

        library.set(
            "logInfo",
            LuaServer.wrap(LOGGER, args -> {
                LOGGER.info(String.valueOf(args.checkstring(1)));

                return LuaValue.NIL;
            })
        );

        library.set(
            "logWarning",
            LuaServer.wrap(LOGGER, args -> {
                LOGGER.warn(String.valueOf(args.checkstring(1)));

                return LuaValue.NIL;
            })
        );

        library.set(
            "logError",
            LuaServer.wrap(LOGGER, args -> {
                LOGGER.error(String.valueOf(args.checkstring(1)));

                return LuaValue.NIL;
            })
        );

        library.set(
            "clientRegister",
            LuaServer.wrap(LOGGER, args -> {
                final LuaValue object = args.arg(5);

                final ArrayList<Subscription> subscriptions = new ArrayList<>();
                if (!object.istable()) {
                    LOGGER.warn("Unknown clientRegister format: " + args);

                    return LuaValue.valueOf("values:nil");
                }

                final LuaTable table = object.checktable();
                for (LuaValue key : table.keys()) {
                    final LuaValue keyValue = table.get(key);
                    if (!keyValue.istable()) {
                        LOGGER.warn("Unknown clientRegister format: " + keyValue);

                        continue;
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
                        String.valueOf(args.arg(1).checkstring()),
                        String.valueOf(args.arg(2).checkstring()),
                        args.arg(3).checkint(),
                        args.arg(4).checkint(),
                        subscriptions
                    )
                );
            })
        );

        library.set(
            "clientDestroy",
            LuaServer.wrap(LOGGER, args -> virtualSystem.clientDestroy(
                String.valueOf(args.checkstring(1)),
                args.checkint(2),
                args.checkint(3)
            ))
        );

        library.set(
            "fetchValues",
            LuaServer.wrap(LOGGER, args -> {
                final ArrayList<Subscription> subscriptions = new ArrayList<>();
                if (!args.istable(1)) {
                    LOGGER.warn("Unknown fetchValues format: " + args);

                    return LuaValue.valueOf("values:nil");
                }

                final LuaTable table = args.checktable(1);
                for (LuaValue key : table.keys()) {
                    final LuaValue keyValue = table.get(key);
                    if (!keyValue.istable()) {
                        return valueOf(
                            "values:{\"%s\"}"
                                .formatted(
                                    globals.load("return _G[\"%s\"]".formatted(keyValue))
                                           .call()
                                )
                        );
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
            })
        );

        library.set(
            "newObject",
            LuaServer.wrap(LOGGER, (arg1, arg2, arg3) -> valueOf(
                virtualSystem.newObject(arg1.checkint(), String.valueOf(arg2.checkstring()), arg3.checkint())
            ))
        );

        library.set(
            "newGate",
            LuaServer.wrap(LOGGER, (arg1, arg2, arg3) -> valueOf(
                virtualSystem.newGate(arg1.checkint(), String.valueOf(arg2.checkstring()))
            ))
        );

        library.set(
            "get",
            LuaServer.wrap(LOGGER, (arg1, arg2) -> virtualSystem.getObject(arg1.checkint()).get(arg2.checkint()))
        );

        library.set(
            "set",
            LuaServer.wrap(LOGGER, (arg1, arg2, arg3) -> {
                virtualSystem.getObject(arg1.checkint()).set(arg2.checkint(), arg3);

                return LuaValue.NIL;
            })
        );

        library.set(
            "execute",
            LuaServer.wrap(LOGGER, (arg1, arg2, arg3) -> virtualSystem.getObject(arg1.checkint()).execute(arg2.checkint(), arg3))
        );

        library.set(
            "addEvent",
            LuaServer.wrap(LOGGER, (arg1, arg2, arg3) -> {
                virtualSystem.getObject(arg1.checkint()).addEvent(arg2.checkint(), arg3.checkfunction());

                return LuaValue.NIL;
            })
        );

        environment.set("api", library);

        return library;
    }
}
