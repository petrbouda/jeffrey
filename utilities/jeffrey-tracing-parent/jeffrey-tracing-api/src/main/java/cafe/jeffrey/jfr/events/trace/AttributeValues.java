/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.jfr.events.trace;

import java.util.Set;

/**
 * Writes an arbitrary runtime value onto a span as an attribute, the same way wherever it comes
 * from.
 * <p>
 * Emitters record values a developer handed the application rather than values the library chose —
 * the MyBatis interceptor captures statement parameters, the servlet filter captures request
 * attributes. They write into the same field of the same recording and are read side by side in the
 * same dashboards, so the rules for turning an object into JSON belong in one place — a number
 * should not be a number in one and a quoted string in the other because two files drifted apart.
 * <p>
 * What is <em>not</em> here is anything domain-specific: only the caller knows that a JDBC
 * parameter may be a {@code Blob} that must never be read, or what a sensible length limit is for
 * its own kind of value. Those decisions stay with the caller, which passes the result here.
 */
public final class AttributeValues {

    /** Recorded when a value's own {@code toString()} throws — instrumentation never does. */
    public static final String UNRENDERABLE_VALUE = "<unavailable>";

    /** Written as JSON numbers; every other Number is rendered as text rather than losing digits. */
    private static final Set<Class<?>> INTEGRAL_TYPES = Set.of(Byte.class, Short.class, Integer.class, Long.class);
    private static final Set<Class<?>> DECIMAL_TYPES = Set.of(Float.class, Double.class);

    private AttributeValues() {
    }

    /**
     * Records {@code value} under {@code key}, as JSON {@code null}, a number, a boolean, or text.
     * <p>
     * The value is recorded whole. Nothing here shortens it: a recorded value exists to be read and
     * matched against, and a silently cut one is a value no search could find again. A payload that
     * should not reach a recording at all is refused at its source — the way MyBatis names a
     * {@code Clob} rather than reading it — not trimmed on the way past.
     */
    public static void put(EventAttributes attributes, String key, Object value) {
        switch (value) {
            case null -> attributes.put(key, (String) null);
            case Boolean flag -> attributes.put(key, (boolean) flag);
            case Number number when INTEGRAL_TYPES.contains(number.getClass()) ->
                    attributes.put(key, number.longValue());
            case Number number when DECIMAL_TYPES.contains(number.getClass()) ->
                    attributes.put(key, number.doubleValue());
            default -> attributes.put(key, text(value));
        }
    }

    /**
     * The value as text.
     */
    public static String text(Object value) {
        try {
            return String.valueOf(value);
        } catch (Throwable failure) {
            // The value's own toString() threw. That is its problem, not the caller's.
            return UNRENDERABLE_VALUE;
        }
    }
}
