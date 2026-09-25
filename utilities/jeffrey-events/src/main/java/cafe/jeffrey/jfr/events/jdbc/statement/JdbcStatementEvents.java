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

package cafe.jeffrey.jfr.events.jdbc.statement;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Picks the statement event class for a SQL verb, so an interceptor that sees every statement
 * through one hook does not have to hand-roll the verb-to-event mapping — the mapping is the
 * library's own convention, and each emitter re-deriving it is how the conventions drift.
 *
 * <pre>{@code
 * // From a framework that knows the command type (MyBatis SqlCommandType, jOOQ, ...):
 * JdbcBaseEvent event = JdbcStatementEvents.forVerb(
 *         statement.getSqlCommandType().name(), name, group);
 *
 * // From a plain JDBC interception point that only has the SQL text:
 * JdbcBaseEvent event = JdbcStatementEvents.forSql(sql, name, group);
 * }</pre>
 *
 * Anything that is not a recognized data verb — DDL, {@code MERGE}, vendor commands — lands on
 * {@link JdbcExecuteEvent}, the catch-all the Database dashboard shows as "other".
 */
public final class JdbcStatementEvents {

    @FunctionalInterface
    private interface EventConstructor {

        JdbcBaseEvent create(String name, String group);
    }

    /**
     * {@code WITH} counts as a query: a common-table expression is how analytical SELECTs are
     * written, and classifying it as "other" would hide exactly the statements worth watching.
     */
    private static final Map<String, EventConstructor> CONSTRUCTORS_BY_VERB = Map.of(
            "SELECT", JdbcQueryEvent::new,
            "WITH", JdbcQueryEvent::new,
            "INSERT", JdbcInsertEvent::new,
            "UPDATE", JdbcUpdateEvent::new,
            "DELETE", JdbcDeleteEvent::new);

    private static final char LINE_COMMENT_CHAR = '-';
    private static final char BLOCK_COMMENT_CHAR = '/';
    private static final char BLOCK_COMMENT_INNER_CHAR = '*';

    private JdbcStatementEvents() {
    }

    /**
     * The event for a known verb — {@code SELECT}, {@code WITH}, {@code INSERT}, {@code UPDATE},
     * {@code DELETE}, case-insensitively — or a {@link JdbcExecuteEvent} for anything else,
     * {@code null} included.
     *
     * @param verb  the statement's verb, e.g. a MyBatis {@code SqlCommandType} name
     * @param name  the statement label — stable and low-cardinality, never SQL text
     * @param group the label statements are grouped under in the Database dashboard
     */
    public static JdbcBaseEvent forVerb(String verb, String name, String group) {
        Objects.requireNonNull(name, "name must not be null");

        EventConstructor constructor = verb == null
                ? null
                : CONSTRUCTORS_BY_VERB.get(verb.toUpperCase(Locale.ROOT));
        return constructor != null ? constructor.create(name, group) : new JdbcExecuteEvent(name, group);
    }

    /**
     * The form of {@link #forVerb} for an interception point that only has the SQL text: the verb
     * is the statement's first keyword, read past leading whitespace, line comments
     * ({@code -- ...}) and block comments (<code>/&#42; ... &#42;/</code>). The SQL itself is used
     * for nothing else — the statement's <em>name</em> stays the caller's label, never the text.
     */
    public static JdbcBaseEvent forSql(String sql, String name, String group) {
        return forVerb(sql == null ? null : firstKeyword(sql), name, group);
    }

    private static String firstKeyword(String sql) {
        int i = 0;
        int length = sql.length();
        while (i < length) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c) || c == '(') {
                i++;
            } else if (c == LINE_COMMENT_CHAR && i + 1 < length && sql.charAt(i + 1) == LINE_COMMENT_CHAR) {
                i = skipLineComment(sql, i);
            } else if (c == BLOCK_COMMENT_CHAR && i + 1 < length && sql.charAt(i + 1) == BLOCK_COMMENT_INNER_CHAR) {
                i = skipBlockComment(sql, i);
            } else {
                break;
            }
        }

        int start = i;
        while (i < length && Character.isLetter(sql.charAt(i))) {
            i++;
        }
        return sql.substring(start, i);
    }

    private static int skipLineComment(String sql, int from) {
        int newline = sql.indexOf('\n', from);
        return newline < 0 ? sql.length() : newline + 1;
    }

    private static int skipBlockComment(String sql, int from) {
        int end = sql.indexOf("*/", from + 2);
        return end < 0 ? sql.length() : end + 2;
    }
}
