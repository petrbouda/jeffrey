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

package cafe.jeffrey.jfr.events.jdbc.datasource;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides what a statement is <em>called</em>.
 * <p>
 * The name is the operation's identity in Jeffrey, so it has to be stable and low-cardinality —
 * one name per kind of statement, never one per execution. Raw SQL is the wrong answer twice over:
 * a statement with inlined literals produces a distinct name per parameter value, and even
 * placeholder SQL is long enough to bloat the JFR per-chunk constant pool.
 * <p>
 * A {@code DataSource} proxy sees only the SQL text, which is why naming is an interface: an
 * application that knows better — a repository name, a MyBatis statement id — supplies its own.
 */
@FunctionalInterface
public interface StatementNaming {

    /**
     * @param sql the statement about to run; never {@code null}
     * @return a stable, low-cardinality name
     */
    String name(String sql);

    /**
     * Names a statement by its verb and primary table — {@code "SELECT users"},
     * {@code "INSERT orders"} — which is the most specific thing that can be read off SQL without
     * becoming per-execution.
     * <p>
     * A statement whose table cannot be read (a DDL statement, a vendor command, a shape the
     * patterns do not cover) is named by its verb alone rather than by its text, so an unparsed
     * statement degrades to a coarse name instead of a high-cardinality one.
     */
    static StatementNaming verbAndTable() {
        return VerbAndTable.INSTANCE;
    }

    /**
     * The implementation behind {@link #verbAndTable()}, kept out of the interface so the patterns
     * are compiled once and named.
     */
    final class VerbAndTable implements StatementNaming {

        private static final VerbAndTable INSTANCE = new VerbAndTable();

        /** Unquoted identifier, optionally schema-qualified. */
        private static final String TABLE = "([A-Za-z0-9_$.\"`\\[\\]]+)";

        private static final Pattern LEADING_VERB = Pattern.compile("^\\s*([A-Za-z]+)");

        /**
         * Where each verb keeps its primary table. {@code SELECT} and {@code DELETE} both read it
         * after {@code FROM}, so they share a pattern.
         */
        private static final Map<String, Pattern> TABLE_BY_VERB = Map.of(
                "SELECT", Pattern.compile("\\bFROM\\s+" + TABLE, Pattern.CASE_INSENSITIVE),
                "DELETE", Pattern.compile("\\bFROM\\s+" + TABLE, Pattern.CASE_INSENSITIVE),
                "INSERT", Pattern.compile("\\bINTO\\s+" + TABLE, Pattern.CASE_INSENSITIVE),
                "UPDATE", Pattern.compile("\\bUPDATE\\s+" + TABLE, Pattern.CASE_INSENSITIVE),
                "MERGE", Pattern.compile("\\bINTO\\s+" + TABLE, Pattern.CASE_INSENSITIVE));

        private static final String UNKNOWN_STATEMENT = "statement";
        private static final String SEPARATOR = " ";

        private VerbAndTable() {
        }

        @Override
        public String name(String sql) {
            if (sql == null || sql.isBlank()) {
                return UNKNOWN_STATEMENT;
            }

            Matcher verbMatcher = LEADING_VERB.matcher(sql);
            if (!verbMatcher.find()) {
                return UNKNOWN_STATEMENT;
            }
            String verb = verbMatcher.group(1).toUpperCase(Locale.ROOT);

            Pattern tablePattern = TABLE_BY_VERB.get(verb);
            if (tablePattern == null) {
                return verb;
            }
            Matcher tableMatcher = tablePattern.matcher(sql);
            return tableMatcher.find() ? verb + SEPARATOR + tableMatcher.group(1) : verb;
        }
    }
}
