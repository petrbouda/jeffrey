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
    /**
     * Some carrier in the group has a different value — true of nearly every trace for a key like
     * {@code status}. The universal reading ("no carrier has this value") is {@link #NONE_EQ}.
     */
    NOT_EQ("value_text <> :%s"),
    /**
     * No carrier in the group has this value: the row predicate is {@link #EQ}'s, and the query
     * builder counts the matches to zero instead of above it. A group with no rows of the key's
     * carrier at all (a trace with no notifications, for a notification key) is not matched — the
     * search runs over the carriers the trace recorded, not over their absence.
     */
    NONE_EQ("value_text = :%s"),
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

    /**
     * Whether the predicate compares {@code value_text} — the column that lives in the value
     * dictionary rather than in the attribute index. A query builder wraps such a predicate in a
     * dictionary lookup; the numeric ones read {@code value_num} straight off the index, and
     * {@link #EXISTS} reads nothing at all.
     */
    public boolean readsText() {
        return this == EQ || this == NOT_EQ || this == NONE_EQ || this == CONTAINS;
    }

    /**
     * Whether the condition holds when its row predicate matches <em>nothing</em> in the group —
     * the query builder counts the matching rows to zero instead of above it, and the hit lookup
     * skips the condition, there being no matching row to point at.
     */
    public boolean negatedAggregate() {
        return this == NONE_EQ;
    }
}
