/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The other half of the conformance question. {@link SpringAiToolConformanceTest} asks whether the two
 * readings <em>describe</em> a tool the same way; this asks whether they <em>call</em> it the same way.
 * <p>
 * A schema that matches and a binder that does not is the worse failure of the two: the model is told
 * the truth about the arguments and then the tool receives something else. Jeffrey binds arguments
 * itself, in {@code ToolParamTypes}, so the binding is a second copied contract — and this runs the
 * same method through Spring AI's own {@link MethodToolCallback} and through Jeffrey's toolset with
 * identical JSON, comparing what the tool actually received.
 * <p>
 * The fixture rather than the real families, deliberately: a real tool needs a live profile database,
 * and what is under test here is the binder, which is shared by all of them and has no idea what a
 * profile is.
 */
class SpringAiToolBindingTest {

    private final BindingProbe jeffreyTarget = new BindingProbe();
    private final BindingProbe springAiTarget = new BindingProbe();

    private final ReflectiveToolset jeffrey = new ReflectiveToolset(jeffreyTarget, "probe");

    /**
     * The same call, made both ways.
     *
     * @return what each side reported the tool received
     */
    private Bound bind(String methodName, String jsonArguments) {
        String viaJeffrey = jeffrey.call("probe_" + methodName, Json.readTree(jsonArguments));
        String viaSpringAi = unwrap(springAiCallback(methodName).call(jsonArguments));
        return new Bound(viaJeffrey, viaSpringAi);
    }

    private record Bound(String viaJeffrey, String viaSpringAi) {

        void agree() {
            assertEquals(viaSpringAi, viaJeffrey,
                    "Spring AI and Jeffrey bound the same JSON arguments differently");
        }
    }

    private MethodToolCallback springAiCallback(String methodName) {
        Method method = Stream.of(BindingProbe.class.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        return MethodToolCallback.builder()
                .toolDefinition(ToolDefinitions.from(method))
                .toolMethod(method)
                .toolObject(springAiTarget)
                .build();
    }

    /**
     * Spring AI runs a result through its result converter, which renders a {@code String} as a JSON
     * string. Jeffrey hands the same value to the MCP content block as it is, so the two are compared
     * on the value rather than on its rendering.
     */
    private static String unwrap(String converted) {
        JsonNode node = Json.readTree(converted);
        return node.isString() ? node.asString() : converted;
    }

    @Nested
    class Strings {

        @Test
        void bindsAStringArgument() {
            bind("echo", "{\"value\":\"hello\"}").agree();
        }

        @Test
        void bindsAStringCarryingJsonPunctuation() {
            bind("echo", "{\"value\":\"a \\\"quoted\\\" {value}\"}").agree();
        }

        @Test
        void bindsAnOmittedOptionalStringAsNull() {
            bind("echo", "{}").agree();
        }
    }

    @Nested
    class Numbers {

        @Test
        void bindsABoxedInteger() {
            bind("boxedNumbers", "{\"count\":42,\"size\":7,\"ratio\":0.25}").agree();
        }

        @Test
        void bindsOmittedBoxedNumbersAsNull() {
            bind("boxedNumbers", "{}").agree();
        }

        @Test
        void bindsPrimitiveNumbers() {
            bind("primitiveNumbers", "{\"count\":42,\"size\":7,\"ratio\":0.25}").agree();
        }

        @Test
        void bindsANegativeValue() {
            bind("boxedNumbers", "{\"count\":-1,\"size\":-2,\"ratio\":-0.5}").agree();
        }

        /**
         * The type that had Jeffrey advertising a number and binding a string, which is the defect the
         * single parameter table was introduced to make impossible.
         */
        @Test
        void bindsAFloat() {
            bind("floats", "{\"ratio\":0.25}").agree();
        }
    }

    @Nested
    class Booleans {

        @Test
        void bindsABoxedBoolean() {
            bind("flags", "{\"boxed\":true,\"primitive\":true}").agree();
        }

        @Test
        void bindsAnOmittedBoxedBooleanAsNull() {
            bind("flags", "{\"primitive\":true}").agree();
        }

        @Test
        void bindsFalseAsFalseRatherThanAsAbsent() {
            bind("flags", "{\"boxed\":false,\"primitive\":false}").agree();
        }
    }

    @Nested
    class Enums {

        @Test
        void bindsAnEnumByName() {
            bind("sort", "{\"order\":\"DESC\"}").agree();
        }

        @Test
        void bindsAnOmittedEnumAsNull() {
            bind("sort", "{}").agree();
        }
    }

    @Test
    void bindsEveryArgumentOfAWideSignatureAtOnce() {
        bind("wide", """
                {"text":"x","count":1,"size":2,"ratio":0.5,"flag":true,"order":"ASC"}""").agree();
    }

    /**
     * Every parameter shape a Jeffrey tool is allowed to declare, reporting exactly what it received so
     * the two binders can be compared on the values rather than on a side effect.
     */
    public static class BindingProbe {

        @Tool(description = "Reports the string it was given")
        public String echo(@ToolParam(required = false, description = "a string") String value) {
            return "value=" + value;
        }

        @Tool(description = "Reports the boxed numbers it was given")
        public String boxedNumbers(
                @ToolParam(required = false, description = "an integer") Integer count,
                @ToolParam(required = false, description = "a long") Long size,
                @ToolParam(required = false, description = "a double") Double ratio) {
            return "count=" + count + " size=" + size + " ratio=" + ratio;
        }

        @Tool(description = "Reports the primitive numbers it was given")
        public String primitiveNumbers(
                @ToolParam(required = false, description = "an int") int count,
                @ToolParam(required = false, description = "a long") long size,
                @ToolParam(required = false, description = "a double") double ratio) {
            return "count=" + count + " size=" + size + " ratio=" + ratio;
        }

        @Tool(description = "Reports the float it was given")
        public String floats(@ToolParam(required = false, description = "a float") Float ratio) {
            return "ratio=" + ratio;
        }

        @Tool(description = "Reports the booleans it was given")
        public String flags(
                @ToolParam(required = false, description = "boxed") Boolean boxed,
                @ToolParam(required = false, description = "primitive") boolean primitive) {
            return "boxed=" + boxed + " primitive=" + primitive;
        }

        @Tool(description = "Reports the enum it was given")
        public String sort(@ToolParam(required = false, description = "an order") Order order) {
            return "order=" + order;
        }

        @Tool(description = "Reports every shape at once")
        public String wide(
                @ToolParam(required = false, description = "text") String text,
                @ToolParam(required = false, description = "count") Integer count,
                @ToolParam(required = false, description = "size") Long size,
                @ToolParam(required = false, description = "ratio") Double ratio,
                @ToolParam(required = false, description = "flag") Boolean flag,
                @ToolParam(required = false, description = "order") Order order) {
            return "text=" + text + " count=" + count + " size=" + size
                    + " ratio=" + ratio + " flag=" + flag + " order=" + order;
        }
    }

    public enum Order {
        ASC, DESC
    }
}
