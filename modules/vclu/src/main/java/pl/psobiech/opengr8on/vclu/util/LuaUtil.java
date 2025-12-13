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

package pl.psobiech.opengr8on.vclu.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.luaj.vm2.*;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.luajc.LuaJC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.util.Util;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.apache.commons.lang3.StringUtils.stripToNull;

public class LuaUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(LuaUtil.class);

    private static final String NIL_AS_STRING = "nil";

    private static final String TABLE_DELIMITER = ",";

    private static final String ENTRY_DELIMITER = "=";

    private LuaUtil() {
        // NOP
    }

    /**
     * @return true, if the luaValue is true, != 0, "true"
     */
    public static boolean trueish(LuaValue luaValue) {
        if (isNil(luaValue)) {
            return false;
        }

        return (luaValue.isboolean() && luaValue.checkboolean())
                || (luaValue.isnumber() && luaValue.checklong() != 0)
                || (luaValue.isstring() && Boolean.parseBoolean(luaValue.checkjstring()));
    }

    /**
     * @return value converted to Lua-String, with String quoted or nil
     */
    public static String stringify(Object value) {
        return stringify(fromObject(value));
    }

    /**
     * @return luaValue converted to String, with String quoted or nil
     */
    public static String stringify(LuaValue luaValue) {
        return stringify(
                luaValue,
                jstring -> "\"" + stringEscape(jstring) + "\"");
    }

    private static String stringEscape(String string) {
        return string.replaceAll("\"", Matcher.quoteReplacement("\\\""));
    }

    /**
     * @return luaValue converted to String, or nil
     */
    public static String stringifyRaw(LuaValue luaValue) {
        return stringify(luaValue, UnaryOperator.identity());
    }

    private static String stringify(LuaValue luaValue, Function<String, String> transformString) {
        if (isNil(luaValue)) {
            return NIL_AS_STRING;
        }

        if (luaValue.isint()) {
            return String.valueOf(luaValue.checkint());
        }

        if (luaValue.islong()) {
            return String.valueOf(luaValue.checklong());
        }

        if (luaValue.isnumber()) {
            return asDouble(luaValue).tojstring();
        }

        if (luaValue.isboolean()) {
            return String.valueOf(luaValue.checkboolean());
        }

        if (luaValue.istable()) {
            return stringifyTable(luaValue.checktable());
        }

        return transformString.apply(luaValue.tojstring());
    }

    private static String stringifyTable(LuaTable table) {
        final Map<LuaValue, LuaValue> map = new HashMap<>();
        final List<LuaValue> list = new ArrayList<>(table.length());

        boolean intKeys = true;
        LuaValue[] keys = table.keys();
        for (int i = 0; i < keys.length; i++) {
            final LuaValue key = keys[i];
            final LuaValue value = table.get(key);

            map.put(key, value);
            list.add(value);

            if (!key.isint() || (key.checkint() - 1) != i) {
                intKeys = false;
            }

        }

        if (intKeys) {
            return stringifyList(list, LuaUtil::stringify);
        }

        return stringifyMap(map, LuaUtil::stringify, LuaUtil::stringify);
    }

    /**
     * @return luaValue converted to String, with String quoted or nil
     */
    public static String stringifyArgs(Varargs args) {
        if (args == null || args.narg() == 0) {
            return "{}";
        }

        return stringifyList(
                IntStream.rangeClosed(1, args.narg())
                         .mapToObj(args::arg)
                         .collect(Collectors.toList()),
                LuaUtil::stringify
        );
    }

    public static <T> String stringifyList(List<T> list, Function<T, String> toString) {
        return "{" + Util.stringifyList(list, TABLE_DELIMITER, toString) + "}";
    }

    public static <V1 extends T, V2 extends T, T> String stringifyMap(Map<V1, V2> map, Function<T, String> toStringKey, Function<T, String> toStringValue) {
        return "{" + Util.stringifyMap(map, TABLE_DELIMITER, ENTRY_DELIMITER, key -> "[" + toStringKey.apply(key) + "]", toStringValue::apply) + "}";
    }

    public static Map<String, String> asStringMap(LuaValue luaValue) {
        if (isNil(luaValue)) {
            return Map.of();
        }

        final LuaTable table = luaValue.checktable();
        final Map<String, String> map = new HashMap<>();
        for (LuaValue key : table.keys()) {
            final LuaValue value = table.get(key);

            map.put(key.tojstring(), value.tojstring());
        }

        return Collections.unmodifiableMap(map);
    }

    /**
     * @return String value of object (if supported, otherwise reverts to String)
     */
    public static Object asObject(LuaValue luaValue) {
        if (isNil(luaValue)) {
            return null;
        }

        if (luaValue.isint()) {
            return luaValue.checkint();
        }

        if (luaValue.islong()) {
            return luaValue.checklong();
        }

        if (luaValue.isnumber()) {
            return asDouble(luaValue).checkdouble();
        }

        if (luaValue.isboolean()) {
            return luaValue.checkboolean();
        }

        if (luaValue.istable()) {
            final LuaTable table = luaValue.checktable();
            final Map<Object, Object> map = new HashMap<>();
            final List<Object> list = new ArrayList<>(table.length());

            boolean intKeys = true;
            LuaValue[] keys = table.keys();
            for (int i = 0; i < keys.length; i++) {
                final LuaValue key = keys[i];
                final LuaValue value = table.get(key);

                final Object valueObject = asObject(value);
                map.put(asObject(key), valueObject);
                list.add(valueObject);

                if (!key.isint() || (key.checkint() - 1) != i) {
                    intKeys = false;
                }
            }

            if (intKeys && !list.isEmpty()) {
                return list;
            }

            return map;
        }

        return luaValue.tojstring();
    }

    public static boolean nonNull(LuaValue luaValue) {
        return !isNil(luaValue);
    }

    public static boolean isNil(LuaValue luaValue) {
        return luaValue == null || luaValue.isnil() || luaValue.narg() == 0;
    }

    public static LuaValue parse(String returnValue) {
        returnValue = stripToNull(returnValue);
        if (returnValue == null || returnValue.equals("nil")) {
            return LuaValue.NIL;
        }

        if (returnValue.equals("nan")) {
            return LuaDouble.NAN;
        }

        if (returnValue.startsWith("#") && returnValue.length() == 7) {
            return LuaValue.valueOf(returnValue);
        }

        try {
            return fromObject(
                    Long.parseLong(returnValue)
            );
        } catch (NumberFormatException ignored) {
            // NOP
        }

        try {
            return fromObject(
                    Double.parseDouble(returnValue)
            );
        } catch (NumberFormatException ignored) {
            // NOP
        }

        try {
            return createContext().load("return %s".formatted(returnValue)).call();
        } catch (Exception e) {
            LOGGER.debug("Not a proper LuaValue: {}, falling back to string", returnValue, e);
        }

        return LuaValue.valueOf(returnValue);
    }

    private static Globals createContext() {
        final Globals globals = new Globals();
        globals.compiler = LuaC.instance;
        globals.loader = LuaJC.instance;
        globals.undumper = LoadState.instance;

        return globals;
    }

    /**
     * @return String value of object (if supported, otherwise reverts to String)
     */
    public static LuaValue fromObject(Object value) {
        return switch (value) {
            case null -> LuaValue.NIL;
            case LuaValue luaValue -> luaValue;
            case JsonNode jsonNode -> fromJson(jsonNode);
            case String string -> LuaValue.valueOf(string);
            case Integer number -> LuaValue.valueOf(number);
            case Long number -> LuaValue.valueOf(number);
            case Double doubleValue -> asDouble(doubleValue);
            case Float number -> LuaValue.valueOf(number);
            case Boolean bool -> LuaValue.valueOf(bool);
            case Collection<?> list -> {
                final LuaValue[] luaValues = new LuaValue[list.size()];

                int i = 0;
                for (Object element : list) {
                    luaValues[i++] = fromObject(element);
                }

                yield LuaValue.tableOf(null, luaValues);
            }
            case Map<?, ?> map -> {
                final LuaTable table = LuaValue.tableOf();
                for (Entry<?, ?> entry : map.entrySet()) {
                    final Object key = entry.getKey();
                    if (key == null) {
                        continue;
                    }

                    table.set(fromObject(key), fromObject(entry.getValue()));
                }

                yield table;
            }
            default -> LuaValue.valueOf(String.valueOf(value));
        };
    }

    /**
     * @return lua value
     */
    public static LuaValue fromJson(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull()) {
            return LuaValue.NIL;
        }

        if (jsonNode.isInt()) {
            return LuaValue.valueOf(jsonNode.asInt());
        }

        if (jsonNode.isLong()) {
            return LuaValue.valueOf(jsonNode.asLong());
        }

        if (jsonNode.isDouble() || jsonNode.isFloat()) {
            final double doubleValue = jsonNode.asDouble();
            if (doubleValue < (double) -Float.MAX_VALUE) {
                return LuaDouble.NEGINF;
            }

            if (doubleValue > (double) Float.MAX_VALUE) {
                return LuaDouble.POSINF;
            }

            return asDouble(doubleValue);
        }

        if (jsonNode.isBoolean()) {
            return LuaValue.valueOf(jsonNode.asBoolean());
        }

        if (jsonNode.isArray()) {
            final ArrayNode arrayNode = (ArrayNode) jsonNode;
            final LuaValue[] luaValues = new LuaValue[arrayNode.size()];
            for (int i = 0; i < arrayNode.size(); i++) {
                final JsonNode element = arrayNode.get(i);

                luaValues[i] = fromJson(element);
            }

            return LuaValue.tableOf(null, luaValues);
        }

        if (jsonNode.isObject()) {
            final LuaTable table = LuaValue.tableOf();

            final ObjectNode objectNode = (ObjectNode) jsonNode;
            final Iterator<String> fieldNameIterator = objectNode.fieldNames();
            while (fieldNameIterator.hasNext()) {
                final String key = fieldNameIterator.next();

                table.set(key, fromJson(objectNode.get(key)));
            }

            return table;
        }

        return LuaValue.valueOf(jsonNode.asText());
    }

    private static LuaNumber asDouble(LuaValue luaValue) {
        return asDouble(luaValue.checkdouble());
    }

    private static LuaNumber asDouble(Double doubleValue) {
        if (doubleValue < (double) -Float.MAX_VALUE) {
            return LuaDouble.NEGINF;
        }

        if (doubleValue > (double) Float.MAX_VALUE) {
            return LuaDouble.POSINF;
        }

        return LuaValue.valueOf(doubleValue);
    }
}
