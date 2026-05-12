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

import java.util.regex.Pattern;

/**
 * Boolean string predicates evaluated in Java. Inputs that arrive as
 * {@code null} produce {@code null} per SQL semantics; callers are expected
 * to use {@link Boolean#TRUE}/{@code FALSE}/{@code null} as needed.
 */
public final class StringPredicates {

    private StringPredicates() {
    }

    public static Boolean startsWith(Object subject, Object prefix) {
        String s = asString(subject);
        String p = asString(prefix);
        if (s == null || p == null) return null;
        return s.startsWith(p);
    }

    public static Boolean endsWith(Object subject, Object suffix) {
        String s = asString(subject);
        String x = asString(suffix);
        if (s == null || x == null) return null;
        return s.endsWith(x);
    }

    public static Boolean contains(Object subject, Object sub) {
        String s = asString(subject);
        String x = asString(sub);
        if (s == null || x == null) return null;
        return s.contains(x);
    }

    public static Boolean matchesRegex(Object subject, Object regex) {
        String s = asString(subject);
        String r = asString(regex);
        if (s == null || r == null) return null;
        return Pattern.compile(r).matcher(s).matches();
    }

    public static Boolean equalsString(Object a, Object b) {
        String s1 = asString(a);
        String s2 = asString(b);
        if (s1 == null || s2 == null) return null;
        return s1.equals(s2);
    }

    public static Boolean equalsIgnoreCase(Object a, Object b) {
        String s1 = asString(a);
        String s2 = asString(b);
        if (s1 == null || s2 == null) return null;
        return s1.equalsIgnoreCase(s2);
    }

    public static Boolean isEmptyString(Object subject) {
        String s = asString(subject);
        if (s == null) return null;
        return s.isEmpty();
    }

    private static String asString(Object v) {
        if (v == null) return null;
        if (v instanceof String s) return s;
        return v.toString();
    }
}
