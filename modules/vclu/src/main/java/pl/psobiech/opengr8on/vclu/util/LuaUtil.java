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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.luaj.vm2.LuaBoolean;
import org.luaj.vm2.LuaDouble;
import org.luaj.vm2.LuaInteger;
import org.luaj.vm2.LuaNumber;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import pl.psobiech.opengr8on.util.Util;

public class LuaUtil {
    private static final String NIL_AS_STRING = "nil";

    private static final String TABLE_DELIMITER = ", ";

    private static final String ENTRY_DELIMITER = "=";

    private LuaUtil() {
        // NOP
    }

    public static Map<String, String> tableStringString(LuaValue luaValue) {
        if (isNil(luaValue)) {
            return Map.of();
        }

        if (luaValue.isstring() && luaValue.checkjstring().isEmpty()) {
            return Map.of();
        }

        final Map<String, String> map = new HashMap<>();
        final LuaTable table = luaValue.checktable();
        for (LuaValue key : table.keys()) {
            final LuaValue value = table.get(key);

            map.put(key.checkjstring(), value.checkjstring());
        }

        return map;
    }

    public static Map<LuaValue, LuaValue> table(LuaValue object) {
        final Map<LuaValue, LuaValue> map = new HashMap<>();

        final LuaTable table = object.checktable();
        for (LuaValue key : table.keys()) {
            final LuaValue value = table.get(key);

            map.put(key, value);
        }

        return map;
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
            return LuaValue.valueOf(jsonNode.asDouble());
        }

        if (jsonNode.isBoolean()) {
            return LuaValue.valueOf(jsonNode.asBoolean());
        }

        if (jsonNode.isArray()) {
            final LuaTable table = LuaValue.tableOf();

            final ArrayNode arrayNode = (ArrayNode) jsonNode;
            for (int i = 0; i < arrayNode.size(); i++) {
                table.set(i, fromJson(arrayNode.get(i)));
            }

            return table;
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
     * @return luaValue converted to String, with String quoted or nil
     */
    public static String stringifyRaw(Varargs args) {
        if (args.narg() == 0) {
            return "{}";
        }

        return stringifyList(
            IntStream.rangeClosed(1, args.narg())
                     .mapToObj(args::arg)
                     .collect(Collectors.toList()),
            LuaUtil::stringify
        );
    }

    /**
     * @return luaValue converted to String, with String quoted or nil
     */
    public static String stringify(LuaValue luaValue) {
        if (isNil(luaValue)) {
            return NIL_AS_STRING;
        }

        if (luaValue instanceof LuaNumber) {
            return String.valueOf(luaValue.checknumber());
        }

        if (luaValue instanceof LuaBoolean) {
            return String.valueOf(luaValue.checkboolean());
        }

        if (luaValue instanceof LuaTable table) {
            return stringifyMap(table(table), LuaUtil::stringifyRaw, LuaUtil::stringify);
        }

        if (luaValue.isstring()) {
            return "\"" + luaValue.checkjstring().replaceAll("\"", Matcher.quoteReplacement("\\\"")) + "\"";
        }

        return String.valueOf(luaValue);
    }

    /**
     * @return luaValue converted to String, or null
     */
    public static String stringifyRaw(LuaValue luaValue) {
        return stringifyRaw(luaValue, null);
    }

    /**
     * @return luaValue converted to String, or nilValue
     */
    public static String stringifyRaw(LuaValue luaValue, String nilValue) {
        if (isNil(luaValue)) {
            return nilValue;
        }

        if (luaValue instanceof LuaNumber) {
            return String.valueOf(luaValue.checknumber());
        }

        if (luaValue instanceof LuaBoolean) {
            return String.valueOf(luaValue.checkboolean());
        }

        if (luaValue instanceof LuaTable table) {
            return stringifyMap(table(table), LuaUtil::stringifyRaw, LuaUtil::stringify);
        }

        if (luaValue.isstring()) {
            return luaValue.checkjstring();
        }

        return String.valueOf(luaValue);
    }

    /**
     * @return String value of object (if supported, otherwise reverts to String)
     */
    public static Object asObject(LuaValue luaValue) {
        if (isNil(luaValue)) {
            return null;
        }

        if (luaValue instanceof LuaDouble) {
            return luaValue.checkdouble();
        }

        if (luaValue instanceof LuaInteger) {
            return luaValue.checkint();
        }

        if (luaValue instanceof LuaBoolean) {
            return luaValue.checkboolean();
        }

        if (luaValue instanceof LuaTable table) {
            final Map<Object, Object> map = new HashMap<>();
            for (LuaValue key : table.keys()) {
                final LuaValue value = table.get(key);

                map.put(asObject(key), asObject(value));
            }

            return map;
        }

        if (luaValue.isstring()) {
            return luaValue.checkjstring();
        }

        return String.valueOf(luaValue);
    }

    public static boolean isNil(LuaValue luaValue) {
        return luaValue == null || luaValue.isnil() || luaValue.narg() == 0;
    }

    /**
     * @return String value of object (if supported, otherwise reverts to String)
     */
    public static LuaValue fromObject(Object object) {
        if (object == null) {
            return LuaValue.NIL;
        }

        if (object instanceof String string) {
            return LuaValue.valueOf(string);
        }

        if (object instanceof Integer number) {
            return LuaValue.valueOf(number);
        }

        if (object instanceof Long number) {
            return LuaValue.valueOf(number);
        }

        if (object instanceof Double number) {
            return LuaValue.valueOf(number);
        }

        if (object instanceof Float number) {
            return LuaValue.valueOf(number);
        }

        if (object instanceof Boolean bool) {
            return LuaValue.valueOf(bool);
        }

        if (object instanceof List<?> list) {
            final LuaTable table = LuaValue.tableOf();
            for (int i = 0; i < list.size(); i++) {
                final Object value = list.get(i);

                table.set(i, fromObject(value));
            }

            return table;
        }

        if (object instanceof Map<?, ?> map) {
            final LuaTable table = LuaValue.tableOf();
            for (Entry<?, ?> entry : map.entrySet()) {
                final Object key = entry.getKey();
                if (key == null) {
                    continue;
                }

                table.set(fromObject(key), fromObject(entry.getValue()));
            }

            return table;
        }

        return LuaValue.valueOf(String.valueOf(object));
    }

    public static <T> String stringifyList(List<T> list, Function<T, String> toString) {
        return "{" + Util.stringifyList(list, TABLE_DELIMITER, toString) + "}";
    }

    public static <T> String stringifyStringMap(Map<String, T> map, Function<T, String> toString) {
        return "{" + Util.stringifyMap(map, TABLE_DELIMITER, ENTRY_DELIMITER, UnaryOperator.identity(), toString) + "}";
    }

    public static <V1 extends T, V2 extends T, T> String stringifyMap(Map<V1, V2> map, Function<T, String> toString) {
        return stringifyMap(map, toString, toString);
    }

    public static <V1 extends T, V2 extends T, T> String stringifyMap(Map<V1, V2> map, Function<T, String> toStringKey, Function<T, String> toStringValue) {
        return "{" + Util.stringifyMap(map, TABLE_DELIMITER, ENTRY_DELIMITER, toStringKey::apply, toStringValue::apply) + "}";
    }
}
