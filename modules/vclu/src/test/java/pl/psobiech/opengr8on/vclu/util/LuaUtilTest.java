package pl.psobiech.opengr8on.vclu.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.luaj.vm2.LuaDouble;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuaUtilTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ParameterizedTest
    @MethodSource("fromJsonValueSource")
    void fromJson(LuaValue expected, JsonNode input) {
        assertEquals(expected, LuaUtil.fromJson(input));
    }

    private static Stream<Arguments> fromJsonValueSource() {
        return Stream.of(
                Arguments.of(LuaValue.NIL, null),
                Arguments.of(LuaValue.NIL, NullNode.getInstance()),
                //
                Arguments.of(LuaValue.valueOf(1), IntNode.valueOf(1)),
                Arguments.of(LuaValue.valueOf(Integer.MIN_VALUE), IntNode.valueOf(Integer.MIN_VALUE)),
                Arguments.of(LuaValue.valueOf(Integer.MAX_VALUE), IntNode.valueOf(Integer.MAX_VALUE)),
                //
                Arguments.of(LuaValue.valueOf(1), LongNode.valueOf(1)),
                Arguments.of(LuaValue.valueOf(Long.MIN_VALUE), LongNode.valueOf(Long.MIN_VALUE)),
                Arguments.of(LuaValue.valueOf(Long.MAX_VALUE), LongNode.valueOf(Long.MAX_VALUE)),
                //
                Arguments.of(LuaValue.valueOf(1.0d), DoubleNode.valueOf(1.0d)),
                Arguments.of(LuaDouble.NEGINF, DoubleNode.valueOf(-Double.MAX_VALUE)),
                Arguments.of(LuaDouble.POSINF, DoubleNode.valueOf(Double.MAX_VALUE)),
                Arguments.of(LuaValue.valueOf(Double.POSITIVE_INFINITY), DoubleNode.valueOf(Double.POSITIVE_INFINITY)),
                Arguments.of(LuaValue.valueOf(Double.NEGATIVE_INFINITY), DoubleNode.valueOf(Double.NEGATIVE_INFINITY)),
                Arguments.of(LuaValue.valueOf(1.0d), FloatNode.valueOf(1.0f)),
                Arguments.of(LuaValue.valueOf(-Float.MAX_VALUE), FloatNode.valueOf(-Float.MAX_VALUE)),
                Arguments.of(LuaValue.valueOf(Float.MAX_VALUE), FloatNode.valueOf(Float.MAX_VALUE)),
                Arguments.of(LuaValue.valueOf(Double.POSITIVE_INFINITY), DoubleNode.valueOf(Float.POSITIVE_INFINITY)),
                Arguments.of(LuaValue.valueOf(Double.NEGATIVE_INFINITY), DoubleNode.valueOf(Float.NEGATIVE_INFINITY)),
                //
                Arguments.of(LuaValue.valueOf(true), BooleanNode.valueOf(true)),
                Arguments.of(LuaValue.valueOf(false), BooleanNode.valueOf(false)),
                //
                Arguments.of(LuaValue.valueOf(""), TextNode.valueOf("")),
                Arguments.of(LuaValue.valueOf("test"), TextNode.valueOf("test"))
                //
        );
    }

    @Test
    void fromJsonArray() {
        final ArrayNode input = OBJECT_MAPPER.createArrayNode()
                                             .add(IntNode.valueOf(1))
                                             .add(TextNode.valueOf("2"))
                                             .add(NullNode.getInstance())
                                             .add(DoubleNode.valueOf(3.0d));

        // when
        final LuaValue result = LuaUtil.fromJson(input);

        // then
        assertTrue(result.istable());
        assertEquals(LuaValue.valueOf(1), result.get(1));
        assertEquals(LuaValue.valueOf("2"), result.get(2));
        assertEquals(LuaValue.NIL, result.get(3));
        assertEquals(LuaValue.valueOf(3.0d), result.get(4));
        assertEquals(LuaValue.NIL, result.get(5));
        assertEquals(LuaValue.NIL, result.get(99));
    }

    @Test
    void fromJsonObject() {
        final ObjectNode input = OBJECT_MAPPER.createObjectNode();
        input.set("ichi", IntNode.valueOf(1));
        input.set("dwa", TextNode.valueOf("2"));
        input.set("czy", NullNode.getInstance());
        input.set("three", DoubleNode.valueOf(3.0d));

        // when
        final LuaValue result = LuaUtil.fromJson(input);

        // then
        assertTrue(result.istable());
        assertEquals(LuaValue.valueOf(1), result.get("ichi"));
        assertEquals(LuaValue.valueOf("2"), result.get("dwa"));
        assertEquals(LuaValue.NIL, result.get("czy"));
        assertEquals(LuaValue.valueOf(3.0d), result.get("three"));
        assertEquals(LuaValue.NIL, result.get("sadasdasd"));
    }

    //

    @ParameterizedTest
    @MethodSource("trueishValueSource")
    void trueish(boolean expected, LuaValue input) {
        assertEquals(expected, LuaUtil.trueish(input));
    }

    private static Stream<Arguments> trueishValueSource() {
        return Stream.of(
                Arguments.of(false, null),
                Arguments.of(false, LuaValue.NIL),
                Arguments.of(false, LuaValue.FALSE),
                Arguments.of(false, LuaValue.valueOf(false)),
                Arguments.of(false, LuaValue.valueOf(0)),
                Arguments.of(false, LuaValue.valueOf(0L)),
                Arguments.of(false, LuaValue.valueOf(0.0f)),
                Arguments.of(false, LuaValue.valueOf(0.0d)),
                Arguments.of(false, LuaValue.valueOf("")),
                Arguments.of(false, LuaValue.valueOf("false")),
                Arguments.of(false, LuaValue.valueOf("faLSe")),
                Arguments.of(false, LuaValue.valueOf("FALSE")),
                Arguments.of(false, LuaValue.valueOf("asdadasdasd")),
                //
                Arguments.of(true, LuaValue.TRUE),
                Arguments.of(true, LuaValue.valueOf(true)),
                Arguments.of(true, LuaValue.valueOf(-1)),
                Arguments.of(true, LuaValue.valueOf(1)),
                Arguments.of(true, LuaValue.valueOf(99)),
                Arguments.of(true, LuaValue.valueOf(99L)),
                Arguments.of(true, LuaValue.valueOf(1.0f)),
                Arguments.of(true, LuaValue.valueOf(1.0d)),
                Arguments.of(true, LuaValue.valueOf("true")),
                Arguments.of(true, LuaValue.valueOf("tRUe")),
                Arguments.of(true, LuaValue.valueOf("TRUE"))
                //
        );
    }

    //

    @ParameterizedTest
    @MethodSource("stringifyValueSource")
    void stringify(String expected, Object input) {
        assertEquals(expected, LuaUtil.stringify(input));
    }

    private static Stream<Arguments> stringifyValueSource() throws Exception {
        return Stream.of(
                Arguments.of("nil", null),
                Arguments.of("nil", NullNode.getInstance()),
                //
                Arguments.of("1", IntNode.valueOf(1)),
                Arguments.of(String.valueOf(Integer.MIN_VALUE), IntNode.valueOf(Integer.MIN_VALUE)),
                Arguments.of(String.valueOf(Integer.MAX_VALUE), IntNode.valueOf(Integer.MAX_VALUE)),
                //
                Arguments.of("1", LongNode.valueOf(1)),
                Arguments.of(String.valueOf(Long.MIN_VALUE), LongNode.valueOf(Long.MIN_VALUE)),
                Arguments.of(String.valueOf(Long.MAX_VALUE), LongNode.valueOf(Long.MAX_VALUE)),
                //
                Arguments.of("1", DoubleNode.valueOf(1.0d)),
                Arguments.of("1.1", DoubleNode.valueOf(1.1d)),
                Arguments.of("-inf", DoubleNode.valueOf(-Double.MAX_VALUE)),
                Arguments.of("inf", DoubleNode.valueOf(Double.MAX_VALUE)),
                Arguments.of("inf", DoubleNode.valueOf(Double.POSITIVE_INFINITY)),
                Arguments.of("-inf", DoubleNode.valueOf(Double.NEGATIVE_INFINITY)),
                Arguments.of("1", FloatNode.valueOf(1.0f)),
                Arguments.of(String.valueOf(-Float.MAX_VALUE), FloatNode.valueOf(-Float.MAX_VALUE)),
                Arguments.of(String.valueOf(Float.MAX_VALUE), FloatNode.valueOf(Float.MAX_VALUE)),
                Arguments.of("inf", DoubleNode.valueOf(Float.POSITIVE_INFINITY)),
                Arguments.of("-inf", DoubleNode.valueOf(Float.NEGATIVE_INFINITY)),
                //
                Arguments.of("true", BooleanNode.valueOf(true)),
                Arguments.of("false", BooleanNode.valueOf(false)),
                //
                Arguments.of("\"\"", TextNode.valueOf("")),
                Arguments.of("\"test\"", TextNode.valueOf("test")),
                //
                Arguments.of("nan", DoubleNode.valueOf(Double.NaN)),
                Arguments.of("nan", FloatNode.valueOf(Float.NaN)),
                //
                Arguments.of("{1,\"dwa\",3.01}", OBJECT_MAPPER.readTree("[1, \"dwa\", 3.01]")),
                Arguments.of("{[1]=1,[3.01]=3.01,[\"dwa\"]=\"dwa\"}", OBJECT_MAPPER.readTree("{\"1\": 1, \"dwa\": \"dwa\", \"3.01\": 3.01 }")),
                //
                Arguments.of("1", 1),
                Arguments.of(String.valueOf(Integer.MIN_VALUE), Integer.MIN_VALUE),
                Arguments.of(String.valueOf(Integer.MAX_VALUE), Integer.MAX_VALUE),
                //
                Arguments.of("1", 1L),
                Arguments.of(String.valueOf(Long.MIN_VALUE), Long.MIN_VALUE),
                Arguments.of(String.valueOf(Long.MAX_VALUE), Long.MAX_VALUE),
                //
                Arguments.of("31", LuaValue.valueOf(31)),
                //
                Arguments.of("1", 1.0d),
                Arguments.of("1.1", 1.1d),
                Arguments.of("-inf", -Double.MAX_VALUE),
                Arguments.of("inf", Double.MAX_VALUE),
                Arguments.of("inf", Double.POSITIVE_INFINITY),
                Arguments.of("-inf", Double.NEGATIVE_INFINITY),
                Arguments.of("1", 1.0f),
                Arguments.of(String.valueOf(-Float.MAX_VALUE), -Float.MAX_VALUE),
                Arguments.of(String.valueOf(Float.MAX_VALUE), Float.MAX_VALUE),
                Arguments.of("inf", Float.POSITIVE_INFINITY),
                Arguments.of("-inf", Float.NEGATIVE_INFINITY),
                //
                Arguments.of(String.valueOf(true), true),
                Arguments.of(String.valueOf(false), false),
                //
                Arguments.of("\"\"", ""),
                Arguments.of("\"test\"", "test"),
                //
                Arguments.of("nan", Double.NaN),
                Arguments.of("nan", Float.NaN),
                //
                Arguments.of("{1,\"dwa\",3.01}", List.of(1, "dwa", 3.01d)),
                Arguments.of("{[1]=1,[2]=\"dwa\",[4]=3.01}", Stream.of(1, "dwa", null, 3.01d).collect(Collectors.toList())),
                Arguments.of("{[1]=1,[3.01]=3.01,[\"dwa\"]=\"dwa\"}", Map.of(1, 1, "dwa", "dwa", 3.01d, 3.01d))
        );
    }

    //

    @ParameterizedTest
    @MethodSource("fromObjectValueSource")
    void fromObject(LuaValue expected, Object input) {
        assertEquals(expected, LuaUtil.fromObject(input));
    }

    private static Stream<Arguments> fromObjectValueSource() throws Exception {
        return Stream.of(
                Arguments.of(LuaValue.NIL, null),
                Arguments.of(LuaValue.NIL, NullNode.getInstance()),
                //
                Arguments.of(LuaValue.valueOf(1), IntNode.valueOf(1)),
                Arguments.of(LuaValue.valueOf(Integer.MIN_VALUE), IntNode.valueOf(Integer.MIN_VALUE)),
                Arguments.of(LuaValue.valueOf(Integer.MAX_VALUE), IntNode.valueOf(Integer.MAX_VALUE)),
                //
                Arguments.of(LuaValue.valueOf(1), LongNode.valueOf(1)),
                Arguments.of(LuaValue.valueOf(Long.MIN_VALUE), LongNode.valueOf(Long.MIN_VALUE)),
                Arguments.of(LuaValue.valueOf(Long.MAX_VALUE), LongNode.valueOf(Long.MAX_VALUE)),
                //
                Arguments.of(LuaValue.valueOf(1), DoubleNode.valueOf(1.0d)),
                Arguments.of(LuaValue.valueOf(1.1), DoubleNode.valueOf(1.1d)),
                Arguments.of(LuaDouble.NEGINF, DoubleNode.valueOf(-Double.MAX_VALUE)),
                Arguments.of(LuaDouble.POSINF, DoubleNode.valueOf(Double.MAX_VALUE)),
                Arguments.of(LuaDouble.POSINF, DoubleNode.valueOf(Double.POSITIVE_INFINITY)),
                Arguments.of(LuaDouble.NEGINF, DoubleNode.valueOf(Double.NEGATIVE_INFINITY)),
                Arguments.of(LuaValue.valueOf(1), FloatNode.valueOf(1.0f)),
                Arguments.of(LuaValue.valueOf(-Float.MAX_VALUE), FloatNode.valueOf(-Float.MAX_VALUE)),
                Arguments.of(LuaValue.valueOf(Float.MAX_VALUE), FloatNode.valueOf(Float.MAX_VALUE)),
                Arguments.of(LuaDouble.POSINF, DoubleNode.valueOf(Float.POSITIVE_INFINITY)),
                Arguments.of(LuaDouble.NEGINF, DoubleNode.valueOf(Float.NEGATIVE_INFINITY)),
                //
                Arguments.of(LuaValue.valueOf(true), BooleanNode.valueOf(true)),
                Arguments.of(LuaValue.valueOf(false), BooleanNode.valueOf(false)),
                //
                Arguments.of(LuaValue.valueOf(""), TextNode.valueOf("")),
                Arguments.of(LuaValue.valueOf("test"), TextNode.valueOf("test")),
                //
                Arguments.of(LuaValue.valueOf(31), LuaValue.valueOf(31)),
                //
                // Arguments.of(LuaDouble.NAN, DoubleNode.valueOf(Double.NaN)),
                // Arguments.of(LuaDouble.NAN, FloatNode.valueOf(Float.NaN)),
                //
                Arguments.of(LuaValue.valueOf(1), 1),
                Arguments.of(LuaValue.valueOf(Integer.MIN_VALUE), Integer.MIN_VALUE),
                Arguments.of(LuaValue.valueOf(Integer.MAX_VALUE), Integer.MAX_VALUE),
                //
                Arguments.of(LuaValue.valueOf(1), 1L),
                Arguments.of(LuaValue.valueOf(Long.MIN_VALUE), Long.MIN_VALUE),
                Arguments.of(LuaValue.valueOf(Long.MAX_VALUE), Long.MAX_VALUE),
                //
                Arguments.of(LuaValue.valueOf(1), 1.0d),
                Arguments.of(LuaValue.valueOf(1.1), 1.1d),
                Arguments.of(LuaDouble.NEGINF, -Double.MAX_VALUE),
                Arguments.of(LuaDouble.POSINF, Double.MAX_VALUE),
                Arguments.of(LuaDouble.POSINF, Double.POSITIVE_INFINITY),
                Arguments.of(LuaDouble.NEGINF, Double.NEGATIVE_INFINITY),
                Arguments.of(LuaValue.valueOf(1), 1.0f),
                Arguments.of(LuaValue.valueOf(-Float.MAX_VALUE), -Float.MAX_VALUE),
                Arguments.of(LuaValue.valueOf(Float.MAX_VALUE), Float.MAX_VALUE),
                Arguments.of(LuaDouble.POSINF, Float.POSITIVE_INFINITY),
                Arguments.of(LuaDouble.NEGINF, Float.NEGATIVE_INFINITY),
                //
                Arguments.of(LuaValue.valueOf(true), true),
                Arguments.of(LuaValue.valueOf(false), false),
                //
                Arguments.of(LuaValue.valueOf(""), ""),
                Arguments.of(LuaValue.valueOf("\""), "\""),
                Arguments.of(LuaValue.valueOf("test"), "test")
                //
                // Arguments.of(LuaDouble.NAN, Double.NaN),
                // Arguments.of(LuaDouble.NAN, Float.NaN)
                //
        );
    }

    //

    @ParameterizedTest
    @MethodSource("stringifyRawValueSource")
    void stringifyRaw(String expected, LuaValue input) {
        assertEquals(expected, LuaUtil.stringifyRaw(input));
    }

    private static Stream<Arguments> stringifyRawValueSource() throws Exception {
        return Stream.of(
                Arguments.of("nil", null),
                //
                Arguments.of(String.valueOf(Integer.MIN_VALUE), LuaValue.valueOf(Integer.MIN_VALUE)),
                Arguments.of(String.valueOf(Integer.MAX_VALUE), LuaValue.valueOf(Integer.MAX_VALUE)),
                //
                Arguments.of(String.valueOf(Long.MIN_VALUE), LuaValue.valueOf(Long.MIN_VALUE)),
                Arguments.of(String.valueOf(Long.MAX_VALUE), LuaValue.valueOf(Long.MAX_VALUE)),
                //
                Arguments.of("1", LuaValue.valueOf(1)),
                Arguments.of("1", LuaValue.valueOf(1.0d)),
                Arguments.of("1.1", LuaValue.valueOf(1.1d)),
                Arguments.of("-inf", LuaValue.valueOf(-Double.MAX_VALUE)),
                Arguments.of("inf", LuaValue.valueOf(Double.MAX_VALUE)),
                Arguments.of("inf", LuaValue.valueOf(Double.POSITIVE_INFINITY)),
                Arguments.of("-inf", LuaValue.valueOf(Double.NEGATIVE_INFINITY)),
                Arguments.of("1", LuaValue.valueOf(1.0f)),
                Arguments.of(String.valueOf(-Float.MAX_VALUE), LuaValue.valueOf(-Float.MAX_VALUE)),
                Arguments.of(String.valueOf(Float.MAX_VALUE), LuaValue.valueOf(Float.MAX_VALUE)),
                Arguments.of("inf", LuaValue.valueOf(Float.POSITIVE_INFINITY)),
                Arguments.of("-inf", LuaValue.valueOf(Float.NEGATIVE_INFINITY)),
                //
                Arguments.of(String.valueOf(true), LuaValue.valueOf(true)),
                Arguments.of(String.valueOf(false), LuaValue.valueOf(false)),
                //
                Arguments.of("", LuaValue.valueOf("")),
                Arguments.of("test", LuaValue.valueOf("test")),
                //
                Arguments.of("nan", LuaValue.valueOf(Double.NaN)),
                Arguments.of("nan", LuaValue.valueOf(Float.NaN))
                //
        );
    }

    //

    @ParameterizedTest
    @MethodSource("parseValueSource")
    void parse(LuaValue expected, String input) {
        assertEquals(expected, LuaUtil.parse(input));
    }

    private static Stream<Arguments> parseValueSource() throws Exception {
        return Stream.of(
                Arguments.of(LuaValue.NIL, null),
                Arguments.of(LuaValue.NIL, "nil"),
                Arguments.of(LuaValue.NIL, " xoxo "),
                //
                Arguments.of(LuaValue.valueOf(1), "1"),
                Arguments.of(LuaValue.valueOf(Integer.MIN_VALUE), String.valueOf(Integer.MIN_VALUE)),
                Arguments.of(LuaValue.valueOf(Integer.MAX_VALUE), String.valueOf(Integer.MAX_VALUE)),
                Arguments.of(LuaValue.valueOf(Long.MIN_VALUE), String.valueOf(Long.MIN_VALUE)),
                Arguments.of(LuaValue.valueOf(Long.MAX_VALUE), String.valueOf(Long.MAX_VALUE)),
                Arguments.of(LuaValue.valueOf(1.0d), String.valueOf(1.0d)),
                Arguments.of(LuaDouble.NEGINF, String.valueOf(-Double.MAX_VALUE)),
                Arguments.of(LuaDouble.POSINF, String.valueOf(Double.MAX_VALUE)),
                Arguments.of(LuaValue.valueOf(Double.POSITIVE_INFINITY), String.valueOf(Double.POSITIVE_INFINITY)),
                Arguments.of(LuaValue.valueOf(Double.NEGATIVE_INFINITY), String.valueOf(Double.NEGATIVE_INFINITY)),
                Arguments.of(LuaValue.valueOf(-Float.MAX_VALUE), String.valueOf((double) -Float.MAX_VALUE)),
                Arguments.of(LuaValue.valueOf(Float.MAX_VALUE), String.valueOf((double) Float.MAX_VALUE)),
                Arguments.of(LuaValue.valueOf(Double.POSITIVE_INFINITY), String.valueOf(Float.POSITIVE_INFINITY)),
                Arguments.of(LuaValue.valueOf(Double.NEGATIVE_INFINITY), String.valueOf(Float.NEGATIVE_INFINITY)),
                //
                Arguments.of(LuaValue.valueOf(true), "true"),
                Arguments.of(LuaValue.valueOf(false), "false"),
                //
                Arguments.of(LuaValue.valueOf(""), "\"\""),
                Arguments.of(LuaValue.valueOf("test"), "\"test\"  "),
                Arguments.of(LuaValue.valueOf("#000000"), "#000000"),
                Arguments.of(LuaValue.valueOf("\"test"), "\"test"),
                Arguments.of(LuaValue.valueOf("{\"test\""), "{\"test\"")
                //
        );
    }

    @Test
    void parseArray1() {
        // when
        final LuaValue result = LuaUtil.parse("{ 1, \"dwa\", 3.01 }");

        // then
        assertTrue(result.istable());
        assertEquals(LuaValue.valueOf(1), result.get(1));
        assertEquals(LuaValue.valueOf("dwa"), result.get(2));
        assertEquals(LuaValue.valueOf(3.01d), result.get(3));
        assertEquals(LuaValue.NIL, result.get(5));
        assertEquals(LuaValue.NIL, result.get(99));
    }

    @Test
    void parseMap() {
        // when
        final LuaValue result = LuaUtil.parse("{[1]=1, [3.01]=3.01, [\"dwa\"]=\"dwa\"}");

        // then
        assertTrue(result.istable());
        assertEquals(LuaValue.valueOf(1), result.get(1));
        assertEquals(LuaValue.valueOf("dwa"), result.get("dwa"));
        assertEquals(LuaValue.valueOf(3.01d), result.get(LuaValue.valueOf(3.01d)));
        assertEquals(LuaValue.NIL, result.get(5));
        assertEquals(LuaValue.NIL, result.get(99));
    }

    //

    @ParameterizedTest
    @MethodSource("asObjectValueSource")
    void asObject(Object expected, LuaValue input) {
        assertEquals(expected, LuaUtil.asObject(input));
    }

    private static Stream<Arguments> asObjectValueSource() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of(null, LuaValue.NIL),
                Arguments.of(false, LuaValue.FALSE),
                Arguments.of(false, LuaValue.valueOf(false)),
                Arguments.of(0, LuaValue.valueOf(0)),
                Arguments.of(((long) Integer.MAX_VALUE) + 1, LuaValue.valueOf(((long) Integer.MAX_VALUE) + 1)),
                Arguments.of("", LuaValue.valueOf("")),
                Arguments.of("false", LuaValue.valueOf("false")),
                Arguments.of("faLSe", LuaValue.valueOf("faLSe")),
                Arguments.of("FALSE", LuaValue.valueOf("FALSE")),
                Arguments.of("asdadasdasd", LuaValue.valueOf("asdadasdasd")),
                //
                Arguments.of(true, LuaValue.TRUE),
                Arguments.of(true, LuaValue.valueOf(true)),
                Arguments.of(-1, LuaValue.valueOf(-1)),
                Arguments.of(1, LuaValue.valueOf(1)),
                Arguments.of(99, LuaValue.valueOf(99)),
                Arguments.of(99, LuaValue.valueOf(99L)),
                Arguments.of(1.100000023841858d, LuaValue.valueOf(1.1f)),
                Arguments.of(1.1d, LuaValue.valueOf(1.1d)),
                //
                Arguments.of(Map.of(), LuaValue.tableOf()),
                Arguments.of(Map.of(), LuaValue.tableOf(null, null)),
                Arguments.of(Map.of(), LuaValue.tableOf(new LuaValue[0], null)),
                Arguments.of(Map.of(), LuaValue.tableOf(null, new LuaValue[0])),
                Arguments.of(Map.of(), LuaValue.tableOf(new LuaValue[0], new LuaValue[0])),
                Arguments.of(Map.of("test", 0), LuaValue.tableOf(new LuaValue[]{LuaValue.valueOf("test"), LuaValue.ZERO})),
                Arguments.of(List.of("test"), LuaValue.tableOf(new LuaValue[]{LuaValue.valueOf(1), LuaValue.valueOf("test")})),
                Arguments.of(Map.of(2, "test"), LuaValue.tableOf(new LuaValue[]{LuaValue.valueOf(2), LuaValue.valueOf("test")})),
                Arguments.of(List.of("test"), LuaValue.tableOf(null, new LuaValue[]{LuaValue.valueOf("test")}))
        );
    }

    //

    @ParameterizedTest
    @MethodSource("stringifyArgsValueSource")
    void stringifyArgs(String expected, Varargs input) {
        assertEquals(expected, LuaUtil.stringifyArgs(input));
    }

    private static Stream<Arguments> stringifyArgsValueSource() throws Exception {
        return Stream.of(
                Arguments.of("{}", null),
                Arguments.of("{}", LuaValue.varargsOf(new LuaValue[0])),
                Arguments.of("{nil}", LuaValue.varargsOf(new LuaValue[]{LuaValue.NIL})),
                Arguments.of("{1}", LuaValue.varargsOf(new LuaValue[]{LuaValue.ONE})),
                Arguments.of("{\"test\",0}", LuaValue.varargsOf(new LuaValue[]{LuaValue.valueOf("test"), LuaValue.ZERO}))
        );
    }

    //

    @ParameterizedTest
    @MethodSource("asStringMapValueSource")
    void asStringMap(Map<String, String> expected, LuaValue input) {
        assertEquals(expected, LuaUtil.asStringMap(input));
    }

    private static Stream<Arguments> asStringMapValueSource() throws Exception {
        return Stream.of(
                Arguments.of(Map.of(), null),
                Arguments.of(Map.of(), LuaValue.tableOf()),
                Arguments.of(Map.of("test", "0"), LuaValue.tableOf(new LuaValue[]{LuaValue.valueOf("test"), LuaValue.ZERO}))
        );
    }

    //

    @ParameterizedTest
    @MethodSource("nonNullValueSource")
    void nonNull(boolean expected, LuaValue input) {
        assertEquals(expected, LuaUtil.nonNull(input));
    }

    private static Stream<Arguments> nonNullValueSource() throws Exception {
        return Stream.of(
                Arguments.of(false, null),
                Arguments.of(false, LuaValue.NIL),
                Arguments.of(false, LuaValue.varargsOf(new LuaValue[0])),
                //
                Arguments.of(true, LuaValue.valueOf("test")),
                Arguments.of(true, LuaValue.valueOf(1))
        );
    }
}