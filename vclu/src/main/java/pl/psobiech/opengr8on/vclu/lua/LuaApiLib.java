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

package pl.psobiech.opengr8on.vclu.lua;

import java.util.ArrayList;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.TwoArgFunction;
import org.slf4j.Logger;
import pl.psobiech.opengr8on.vclu.VirtualSystem;
import pl.psobiech.opengr8on.vclu.VirtualSystem.Subscription;

public class LuaApiLib extends TwoArgFunction {
    private final Logger logger;

    private final VirtualSystem virtualSystem;

    private final Globals globals;

    public LuaApiLib(Logger logger, VirtualSystem virtualSystem, Globals globals) {
        this.logger = logger;
        this.virtualSystem = virtualSystem;
        this.globals = globals;
    }

    @Override
    public LuaValue call(LuaValue moduleName, LuaValue environment) {
        final LuaValue library = tableOf();
        library.set("setup", LuaServer.wrap(logger, this::setup));
        library.set("loop", LuaServer.wrap(logger, this::loop));
        library.set("sleep", LuaServer.wrap(logger, this::sleep));

        library.set("logDebug", LuaServer.wrap(logger, this::logDebug));
        library.set("logInfo", LuaServer.wrap(logger, this::logInfo));
        library.set("logWarning", LuaServer.wrap(logger, this::logWarning));
        library.set("logError", LuaServer.wrap(logger, this::logError));

        library.set("clientRegister", LuaServer.wrap(logger, this::clientRegister));
        library.set("clientDestroy", LuaServer.wrap(logger, this::clientDestroy));
        library.set("fetchValues", LuaServer.wrap(logger, this::fetchValues));

        library.set("newObject", LuaServer.wrap(logger, this::newObject));
        library.set("newGate", LuaServer.wrap(logger, this::newGate));

        library.set("get", LuaServer.wrap(logger, this::_get));
        library.set("set", LuaServer.wrap(logger, this::_set));
        library.set("execute", LuaServer.wrap(logger, this::execute));
        library.set("addEvent", LuaServer.wrap(logger, this::addEvent));

        environment.set("api", library);

        return library;
    }

    public void setup() {
        virtualSystem.setup();
    }

    public void loop() {
        virtualSystem.loop();
    }

    public void sleep(Varargs args) {
        virtualSystem.sleep(args.checklong(1));
    }

    public void logDebug(Varargs args) {
        logger.debug(args.checkjstring(1));
    }

    public void logInfo(Varargs args) {
        logger.info(args.checkjstring(1));
    }

    public void logWarning(Varargs args) {
        logger.warn(args.checkjstring(1));
    }

    public void logError(Varargs args) {
        logger.error(args.checkjstring(1));
    }

    public LuaValue clientRegister(Varargs args) {
        final LuaValue object = args.arg(5);

        final ArrayList<Subscription> subscriptions = new ArrayList<>();
        if (!object.istable()) {
            logger.warn("Unknown clientRegister format: " + args);

            return LuaValue.valueOf("values:nil");
        }

        final LuaTable table = object.checktable();
        for (LuaValue key : table.keys()) {
            final LuaValue keyValue = table.get(key);
            if (!keyValue.istable()) {
                logger.warn("Unknown clientRegister format: " + keyValue);

                continue;
            }

            subscriptions.add(
                new Subscription(
                    keyValue.checktable().get(1).checktable().get("name").checkjstring(),
                    keyValue.checktable().get(2).checkint()
                )
            );
        }

        return LuaValue.valueOf(
            virtualSystem.clientRegister(
                args.checkjstring(1),
                args.checkjstring(2),
                args.checkint(3),
                args.checkint(4),
                subscriptions
            )
        );
    }

    public LuaValue clientDestroy(Varargs args) {
        return virtualSystem.clientDestroy(
            args.checkjstring(1),
            args.checkint(2),
            args.checkint(3)
        );
    }

    public LuaValue fetchValues(Varargs args) {
        final ArrayList<Subscription> subscriptions = new ArrayList<>();
        if (!args.istable(1)) {
            logger.warn("Unknown fetchValues format: " + args);

            return LuaValue.valueOf("values:nil");
        }

        final LuaTable table = args.checktable(1);
        for (LuaValue key : table.keys()) {
            final LuaValue keyValue = table.get(key);
            if (!keyValue.istable()) {
                return LuaValue.valueOf(
                    "values:{\"%s\"}"
                        .formatted(
                            globals.load("return _G[\"%s\"]".formatted(keyValue))
                                   .call()
                        )
                );
            }

            subscriptions.add(
                new Subscription(
                    keyValue.checktable().get(1).checktable().get("name").checkjstring(),
                    keyValue.checktable().get(2).checkint()
                )
            );
        }

        return LuaValue.valueOf(
            "values:" + virtualSystem.fetchValues(subscriptions)
        );
    }

    public void newObject(LuaValue arg1, LuaValue arg2, LuaValue arg3) {
        virtualSystem.newObject(arg1.checkint(), arg2.checkjstring(), arg3.checkint());
    }

    public void newGate(LuaValue arg1, LuaValue arg2, LuaValue arg3) {
        virtualSystem.newGate(arg1.checkint(), arg2.checkjstring());
    }

    public LuaValue _get(LuaValue arg1, LuaValue arg2) {
        return virtualSystem.getObject(arg1.checkjstring())
                            .get(arg2.checkint());
    }

    public LuaValue _set(LuaValue arg1, LuaValue arg2, LuaValue arg3) {
        virtualSystem.getObject(arg1.checkjstring())
                     .set(arg2.checkint(), arg3);

        return LuaValue.NIL;
    }

    public LuaValue execute(Varargs args) {
        return virtualSystem.getObject(args.checkjstring(1))
                            .execute(args.checkint(2), args.subargs(3));
    }

    public void addEvent(LuaValue arg1, LuaValue arg2, LuaValue arg3) {
        virtualSystem.getObject(arg1.checkjstring())
                     .addEventHandler(arg2.checkint(), arg3.checkfunction());
    }
}
