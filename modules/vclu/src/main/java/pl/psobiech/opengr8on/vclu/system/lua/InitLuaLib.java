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

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.function.Consumer;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.TwoArgFunction;
import org.slf4j.Logger;
import pl.psobiech.opengr8on.util.IPv4AddressUtil;
import pl.psobiech.opengr8on.vclu.system.ClientRegistry.Subscription;
import pl.psobiech.opengr8on.vclu.system.VirtualSystem;
import pl.psobiech.opengr8on.vclu.system.lua.fn.LuaVarArgConsumer;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualObject;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;

public class InitLuaLib extends TwoArgFunction {
    private static final String FETCH_VALUES_PREFIX = "values:";

    private final Logger logger;

    private final VirtualSystem virtualSystem;

    private final Globals globals;

    public InitLuaLib(Logger logger, VirtualSystem virtualSystem, Globals globals) {
        this.logger = logger;
        this.virtualSystem = virtualSystem;
        this.globals = globals;
    }

    @Override
    public LuaValue call(LuaValue moduleName, LuaValue environment) {
        registerGlobals(environment);

        environment.set("SYSTEM", createSystem());
        environment.set("OBJECT", createObjectPrototype());
        environment.set("GATE", createGatePrototype());

        return tableOf();
    }

    private void registerGlobals(LuaValue environment) {
        environment.set("collectgarbage", LuaFunctionWrapper.wrap(logger, () -> {
        }));

        environment.set("logDebug", LuaFunctionWrapper.wrap(logger, argsToString(logger::debug)));
        environment.set("logInfo", LuaFunctionWrapper.wrap(logger, argsToString(logger::info)));
        environment.set("log", LuaFunctionWrapper.wrap(logger, argsToString(logger::info)));
        environment.set("logWarning", LuaFunctionWrapper.wrap(logger, argsToString(logger::warn)));
        environment.set("logError", LuaFunctionWrapper.wrap(logger, argsToString(logger::error)));

        // override print function, since its better at showing LuaValue's
        environment.set("print", LuaFunctionWrapper.wrap(logger, argsToString(logger::info)));
    }

    private LuaTable createSystem() {
        final LuaTable system = tableOf();

        system.set("Init", LuaFunctionWrapper.wrap(logger, virtualSystem::setup));
        system.set("Loop", LuaFunctionWrapper.wrap(logger, virtualSystem::loop));
        system.set("Wait", LuaFunctionWrapper.wrap(logger, args -> {
            final long milliseconds = args.checklong(2);

            virtualSystem.sleep(milliseconds);
        }));

        system.set("clientRegister", LuaFunctionWrapper.wrap(logger, this::clientRegister));
        system.set("clientDestroy", LuaFunctionWrapper.wrap(logger, this::clientDestroy));
        system.set("fetchValues", LuaFunctionWrapper.wrap(logger, this::fetchValues));

        system.set("mqttRegister", LuaFunctionWrapper.wrap(logger, args -> {
            logger.warn("Not implemented: mqttRegister({})", argsToString(args));
        }));
        system.set("mqttDestroy", LuaFunctionWrapper.wrap(logger, args -> {
            logger.warn("Not implemented: mqttDestroy({})", argsToString(args));
        }));

        return system;
    }

    private LuaTable createObjectPrototype() {
        final LuaTable prototype = tableOf();
        prototype.set("__index", prototype);
        prototype.set("new", LuaFunctionWrapper.wrap(logger, (args) -> {
            final LuaTable self = tableOf();
            self.setmetatable(args.arg(1));

            final int index = args.checkint(2);
            if (index == 0 || index == 1) {
                final LuaValue objectName = args.arg(4);
                self.set("name", objectName);

                virtualSystem.newObject(
                        index, objectName.checkjstring(),
                        IPv4AddressUtil.parseIPv4(args.checkint(3))
                );
            } else {
                final LuaValue objectName = args.arg(3);
                self.set("name", objectName);

                virtualSystem.newObject(
                        index, objectName.checkjstring(),
                        null
                );
            }

            return self;
        }));

        prototype.set("get", LuaFunctionWrapper.wrap(logger, this::getObjectValue));
        prototype.set("set", LuaFunctionWrapper.wrap(logger, this::setObjectValue));
        prototype.set("execute", LuaFunctionWrapper.wrap(logger, this::executeObjectMethod));
        prototype.set("add_event", LuaFunctionWrapper.wrap(logger, this::registerObjectEvent));

        return prototype;
    }

    private LuaTable createGatePrototype() {
        final LuaTable prototype = tableOf();
        prototype.set("__index", prototype);
        prototype.set("new", LuaFunctionWrapper.wrap(logger, (args) -> {
            final LuaTable self = tableOf();
            self.setmetatable(args.arg(1));

            final LuaValue objectName = args.arg(3);
            self.set("name", objectName);

            virtualSystem.newGate(args.checkint(2), objectName.checkjstring());

            return self;
        }));

        prototype.set("get", LuaFunctionWrapper.wrap(logger, this::getObjectValue));
        prototype.set("set", LuaFunctionWrapper.wrap(logger, this::setObjectValue));
        prototype.set("execute", LuaFunctionWrapper.wrap(logger, this::executeObjectMethod));
        prototype.set("add_event", LuaFunctionWrapper.wrap(logger, this::registerObjectEvent));

        return prototype;
    }

    private static LuaVarArgConsumer argsToString(Consumer<String> consumer) {
        return args -> consumer.accept(argsToString(args));
    }

    private static String argsToString(Varargs args) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= args.narg(); i++) {
            sb.append(
                    LuaUtil.stringifyRaw(args.arg(i))
            );
        }

        return sb.toString();
    }

    public LuaValue clientRegister(Varargs args) {
        final int sessionId = args.checkint(5);

        final LuaValue registrationObject = args.arg(6);
        if (!registrationObject.istable()) {
            logger.warn("Unknown clientRegister format: " + args);

            return LuaValue.valueOf("clientReport:" + sessionId + ":nil");
        }

        final LuaTable registrationTable = registrationObject.checktable();
        final ArrayList<Subscription> subscriptions = new ArrayList<>();
        for (LuaValue key : registrationTable.keys()) {
            final LuaValue keyValue = registrationTable.get(key);
            if (!keyValue.istable()) {
                logger.warn("Ignoring unknown clientRegister format: " + keyValue);

                continue;
            }

            final LuaTable objectTable = keyValue.checktable().get(1).checktable();
            final String objectName = objectTable.get("name").checkjstring();
            final VirtualObject object = virtualSystem.getObject(objectName);

            final int index = keyValue.checktable().get(2).checkint();

            subscriptions.add(
                    new Subscription(object, index)
            );
        }

        final Inet4Address remoteAddress = IPv4AddressUtil.parseIPv4(args.checkjstring(2));
        final Inet4Address address = IPv4AddressUtil.parseIPv4(args.checkjstring(3));
        final int port = args.checkint(4);

        return LuaValue.valueOf(
                virtualSystem.clientRegister(
                        remoteAddress, address, port,
                        sessionId,
                        subscriptions
                )
        );
    }

    public LuaValue clientDestroy(Varargs args) {
        final Inet4Address address = IPv4AddressUtil.parseIPv4(args.checkjstring(2));
        final int port = args.checkint(3);
        final int sessionId = args.checkint(4);

        return virtualSystem.clientDestroy(address, port, sessionId);
    }

    public LuaValue fetchValues(Varargs args) {
        final ArrayList<Subscription> subscriptions = new ArrayList<>();
        if (!args.istable(2)) {
            logger.warn("Unknown fetchValues format: " + args);

            return LuaValue.valueOf(FETCH_VALUES_PREFIX + "nil");
        }

        final LuaTable table = args.checktable(2);
        for (LuaValue key : table.keys()) {
            final LuaValue value = table.get(key);
            if (!value.istable()) {
                return LuaValue.valueOf(
                        FETCH_VALUES_PREFIX + "{%s}"
                                .formatted(
                                        LuaUtil.stringify(
                                                // TODO: sanitize input
                                                globals.load("return %s".formatted(value))
                                                        .call()
                                        )
                                )
                );
            }

            final LuaTable valueTable = value.checktable();
            final LuaTable objectTable = valueTable.get(1).checktable();
            final String objectName = objectTable.get("name").checkjstring();
            final VirtualObject object = virtualSystem.getObject(objectName);

            subscriptions.add(
                    new Subscription(
                            object,
                            valueTable.get(2).checkint()
                    )
            );
        }

        return LuaValue.valueOf(
                FETCH_VALUES_PREFIX + virtualSystem.fetchValues(subscriptions)
        );
    }

    public LuaValue getObjectValue(Varargs args) {
        final LuaTable object = args.checktable(1);
        final String objectName = object.get("name").checkjstring();

        return virtualSystem.getObject(objectName)
                .get(args.checkint(2));
    }

    public LuaValue setObjectValue(Varargs args) {
        final LuaTable object = args.checktable(1);
        final String objectName = object.get("name").checkjstring();

        virtualSystem.getObject(objectName)
                .set(args.checkint(2), args.arg(3));

        return LuaValue.NIL;
    }

    public LuaValue executeObjectMethod(Varargs args) {
        final LuaTable object = args.checktable(1);
        final String objectName = object.get("name").checkjstring();

        final int index = args.checkint(2);
        final Varargs otherArgs = args.subargs(3);

        return virtualSystem.getObject(objectName)
                .execute(index, otherArgs);
    }

    public void registerObjectEvent(Varargs args) {
        final LuaTable object = args.checktable(1);
        final String objectName = object.get("name").checkjstring();

        final int address = args.checkint(2);
        final LuaFunction function = args.checkfunction(3);

        virtualSystem.getObject(objectName)
                .addEventHandler(address, function);
    }
}
