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

package cafe.jeffrey.provider.profile.api;

/**
 * How one condition compares a key against a value.
 * <p>
 * The SQL fragment each one renders is fixed here rather than assembled at the call site: the value
 * always travels as a bound parameter, and keeping the operator itself out of caller reach is what
 * makes a condition safe to build from a query string.
 */
public enum TraceAttributeOperator {

    EQ("value_text = :%s"),
    NOT_EQ("value_text <> :%s"),
    CONTAINS("lower(value_text) LIKE '%%' || lower(:%s) || '%%'"),
    /**
     * The four ordering comparisons read {@code value_num}, which is filled only where the value is
     * a number — so they silently skip a value that is not one, rather than comparing it as text and
     * reporting that {@code "9" > "10000"}.
     */
    GT("value_num > CAST(:%s AS DOUBLE)"),
    GTE("value_num >= CAST(:%s AS DOUBLE)"),
    LT("value_num < CAST(:%s AS DOUBLE)"),
    LTE("value_num <= CAST(:%s AS DOUBLE)"),
    /** The key is present at all, whatever it holds. Takes no value. */
    EXISTS("TRUE");

    private final String predicate;

    TraceAttributeOperator(String predicate) {
        this.predicate = predicate;
    }

    /**
     * @param parameter the name of the bound parameter carrying the value
     * @return the SQL predicate, to be ANDed with the key match
     */
    public String predicate(String parameter) {
        return predicate.formatted(parameter);
    }

    /** Whether this operator reads a value at all — {@link #EXISTS} is the one that does not. */
    public boolean needsValue() {
        return this != EXISTS;
    }

    /** Whether the value has to be a number for the comparison to mean anything. */
    public boolean isNumeric() {
        return this == GT || this == GTE || this == LT || this == LTE;
    }
}
