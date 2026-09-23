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
package cafe.jeffrey.profile.heapdump.oql.executor;

/**
 * Numeric coercions and comparisons used by the Plan C evaluator. Boxed
 * primitives flow through {@link Object} values; this helper unifies them
 * to {@code long} or {@code double} on demand.
 */
final class Numbers {

    private Numbers() {
    }

    static boolean isNumber(Object v) {
        return v instanceof Number || v instanceof Character || v instanceof Boolean;
    }

    static double toDouble(Object v) {
        return switch (v) {
            case Number n -> n.doubleValue();
            case Character c -> (double) c.charValue();
            case Boolean b -> b ? 1.0 : 0.0;
            case null -> 0.0;
            default -> throw new IllegalArgumentException("not a number: " + v + " (" + v.getClass() + ")");
        };
    }

    static long toLong(Object v) {
        return switch (v) {
            case Number n -> n.longValue();
            case Character c -> (long) c.charValue();
            case Boolean b -> b ? 1L : 0L;
            case null -> 0L;
            default -> throw new IllegalArgumentException("not a number: " + v + " (" + v.getClass() + ")");
        };
    }

    /** True iff either operand is fractional (Double/Float). */
    static boolean isFractional(Object left, Object right) {
        return left instanceof Double || left instanceof Float
                || right instanceof Double || right instanceof Float;
    }

    static Object add(Object l, Object r) {
        if (isFractional(l, r)) {
            return toDouble(l) + toDouble(r);
        }
        return toLong(l) + toLong(r);
    }

    static Object sub(Object l, Object r) {
        if (isFractional(l, r)) {
            return toDouble(l) - toDouble(r);
        }
        return toLong(l) - toLong(r);
    }

    static Object mul(Object l, Object r) {
        if (isFractional(l, r)) {
            return toDouble(l) * toDouble(r);
        }
        return toLong(l) * toLong(r);
    }

    static Object div(Object l, Object r) {
        if (isFractional(l, r)) {
            return toDouble(l) / toDouble(r);
        }
        long rl = toLong(r);
        if (rl == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return toLong(l) / rl;
    }

    /**
     * Returns negative / zero / positive for {@code l < r} / {@code l == r} /
     * {@code l > r}. Null follows SQL semantics — any comparison involving
     * null returns null, signalled here by throwing {@link NullValueException}.
     */
    static int compare(Object l, Object r) {
        if (l == null || r == null) {
            throw new NullValueException();
        }
        if (l instanceof String ls && r instanceof String rs) {
            return ls.compareTo(rs);
        }
        if (isNumber(l) && isNumber(r)) {
            if (isFractional(l, r)) {
                return Double.compare(toDouble(l), toDouble(r));
            }
            return Long.compare(toLong(l), toLong(r));
        }
        // Fall back to string compare for mixed types — matches DuckDB-ish coercion.
        return l.toString().compareTo(r.toString());
    }

    static boolean truthy(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.longValue() != 0;
        }
        if (v instanceof String s) {
            return !s.isEmpty();
        }
        return true;
    }

    static final class NullValueException extends RuntimeException {
        NullValueException() {
            super("null operand");
        }
    }
}
