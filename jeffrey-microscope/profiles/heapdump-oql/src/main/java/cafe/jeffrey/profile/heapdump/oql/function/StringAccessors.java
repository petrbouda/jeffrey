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
package cafe.jeffrey.profile.heapdump.oql.function;

/**
 * Plan C string accessors — return string or numeric values from a decoded
 * string, used in projections and chained predicates. All null inputs
 * propagate to {@code null} per SQL semantics.
 */
public final class StringAccessors {

    private StringAccessors() {
    }

    public static Integer stringLength(Object subject) {
        String s = asString(subject);
        return s == null ? null : s.length();
    }

    public static String substring(Object subject, Object start, Object end) {
        String s = asString(subject);
        if (s == null || start == null) return null;
        int from = ((Number) start).intValue();
        int to = end == null ? s.length() : ((Number) end).intValue();
        int safeFrom = Math.max(0, Math.min(from, s.length()));
        int safeTo = Math.max(safeFrom, Math.min(to, s.length()));
        return s.substring(safeFrom, safeTo);
    }

    public static String lower(Object subject) {
        String s = asString(subject);
        return s == null ? null : s.toLowerCase();
    }

    public static String upper(Object subject) {
        String s = asString(subject);
        return s == null ? null : s.toUpperCase();
    }

    public static String trim(Object subject) {
        String s = asString(subject);
        return s == null ? null : s.trim();
    }

    public static Integer indexOf(Object subject, Object sub) {
        String s = asString(subject);
        String x = asString(sub);
        if (s == null || x == null) return null;
        return s.indexOf(x);
    }

    public static Integer lastIndexOf(Object subject, Object sub) {
        String s = asString(subject);
        String x = asString(sub);
        if (s == null || x == null) return null;
        return s.lastIndexOf(x);
    }

    public static String charAt(Object subject, Object index) {
        String s = asString(subject);
        if (s == null || index == null) return null;
        int i = ((Number) index).intValue();
        if (i < 0 || i >= s.length()) return null;
        return String.valueOf(s.charAt(i));
    }

    private static String asString(Object v) {
        if (v == null) return null;
        if (v instanceof String s) return s;
        return v.toString();
    }
}
