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

package cafe.jeffrey.jfr.events.trace;

import java.util.Set;

/**
 * Writes an arbitrary runtime value onto a span as an attribute, the same way wherever it comes
 * from.
 * <p>
 * Two emitters record values a developer handed the application rather than values the library
 * chose: {@code @Traced} captures method arguments, and the MyBatis interceptor captures statement
 * parameters. They write into the same field of the same recording and are read side by side in the
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

    private static final String TRUNCATION_MARKER = "…";

    /** Written as JSON numbers; every other Number is rendered as text rather than losing digits. */
    private static final Set<Class<?>> INTEGRAL_TYPES = Set.of(Byte.class, Short.class, Integer.class, Long.class);
    private static final Set<Class<?>> DECIMAL_TYPES = Set.of(Float.class, Double.class);

    private AttributeValues() {
    }

    /**
     * Records {@code value} under {@code key}, as JSON {@code null}, a number, a boolean, or text.
     *
     * @param maxValueLength longest text recorded before it is truncated and marked
     */
    public static void put(EventAttributes attributes, String key, Object value, int maxValueLength) {
        switch (value) {
            case null -> attributes.put(key, (String) null);
            case Boolean flag -> attributes.put(key, (boolean) flag);
            case Number number when INTEGRAL_TYPES.contains(number.getClass()) ->
                    attributes.put(key, number.longValue());
            case Number number when DECIMAL_TYPES.contains(number.getClass()) ->
                    attributes.put(key, number.doubleValue());
            default -> attributes.put(key, text(value, maxValueLength));
        }
    }

    /**
     * The value as text, truncated to {@code maxValueLength} and marked when it was cut.
     */
    public static String text(Object value, int maxValueLength) {
        String rendered;
        try {
            rendered = String.valueOf(value);
        } catch (Throwable failure) {
            // The value's own toString() threw. That is its problem, not the caller's.
            return UNRENDERABLE_VALUE;
        }

        if (rendered.length() <= maxValueLength) {
            return rendered;
        }

        return rendered.substring(0, maxValueLength) + TRUNCATION_MARKER;
    }
}
