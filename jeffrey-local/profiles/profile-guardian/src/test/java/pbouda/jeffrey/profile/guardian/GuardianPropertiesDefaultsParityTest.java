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

package pbouda.jeffrey.profile.guardian;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link GuardianProperties#defaults()} hand-lists every default by position. When a new field
 * is added to the record, Spring binding picks up the {@code @DefaultValue} annotation
 * automatically — but {@code defaults()} won't: the factory will either fail to compile (too
 * few arguments) or silently construct with a stale default.
 * <p>
 * This test reflects on the record canonical constructor and compares the value of each
 * {@code @DefaultValue} annotation to the matching field value of {@code defaults()}.
 */
class GuardianPropertiesDefaultsParityTest {

    @Test
    void defaultsFactoryMatchesEveryDefaultValueAnnotation() throws ReflectiveOperationException {
        RecordComponent[] components = GuardianProperties.class.getRecordComponents();
        // Canonical ctor parameter order matches record component order.
        Parameter[] ctorParams = GuardianProperties.class
                .getDeclaredConstructor(paramTypes(components))
                .getParameters();

        GuardianProperties defaults = GuardianProperties.defaults();
        List<String> drift = new ArrayList<>();

        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            Parameter param = ctorParams[i];

            DefaultValue ann = param.getAnnotation(DefaultValue.class);
            assertNotNull(ann,
                    "Every GuardianProperties component must carry @DefaultValue — missing on '" + component.getName() + "'");
            assertEquals(1, ann.value().length,
                    "@DefaultValue should provide exactly one value for '" + component.getName() + "'");

            String annotationText = ann.value()[0];
            Object parsed = parse(annotationText, component.getType());
            Object actual = component.getAccessor().invoke(defaults);

            if (!parsed.equals(actual)) {
                drift.add(component.getName()
                        + " — @DefaultValue(\"" + annotationText + "\") = " + parsed
                        + " but defaults().(" + component.getName() + "()) = " + actual);
            }
        }

        if (!drift.isEmpty()) {
            fail("GuardianProperties.defaults() has drifted from the @DefaultValue annotations:\n  " +
                    String.join("\n  ", drift) +
                    "\n\nFix defaults() to match, or add a new @DefaultValue if you added a field.");
        }
    }

    @Test
    void defaultsFactoryHasMatchingArityToRecord() throws ReflectiveOperationException {
        // A belt-and-braces check: if someone adds a new field to the record but forgets to extend
        // defaults(), the canonical ctor call site in defaults() would fail to compile. This test
        // re-asserts arity explicitly so the failure message is clear in CI logs even if the
        // compiler error is missed.
        int expected = GuardianProperties.class.getRecordComponents().length;
        int actual = GuardianProperties.defaults().getClass().getRecordComponents().length;
        assertEquals(expected, actual,
                "defaults() and the record definition must have the same component count");
        assertTrue(expected > 0);
    }

    private static Class<?>[] paramTypes(RecordComponent[] components) {
        Class<?>[] types = new Class[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
        }
        return types;
    }

    private static Object parse(String text, Class<?> type) {
        if (type == double.class || type == Double.class) {
            return Double.parseDouble(text);
        }
        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(text);
        }
        if (type == long.class || type == Long.class) {
            return Long.parseLong(text);
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(text);
        }
        if (type == String.class) {
            return text;
        }
        throw new IllegalStateException("Unsupported default-value component type: " + type);
    }
}
