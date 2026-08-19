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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.TraceAttributeCondition;
import cafe.jeffrey.provider.profile.api.TraceAttributeKeyId;
import cafe.jeffrey.provider.profile.api.TraceAttributeSearchQuery;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns attribute conditions into SQL.
 * <p>
 * Split out of the repository because both shapes the conditions are used in read the same
 * fragments: the matching query ANDs them across a grouping, and the hit lookup ORs them across
 * rows. Building each separately is how the two silently drifted apart in the first place — a
 * condition that narrowed the list but was never highlighted on the span that satisfied it.
 * <p>
 * Every value travels as a bound parameter and every operator comes from
 * {@link cafe.jeffrey.provider.profile.api.TraceAttributeOperator}, so nothing a caller types
 * reaches the statement as text.
 */
final class TraceAttributeQueries {

    /**
     * The JSON path to one key of the payload, quoted.
     * <p>
     * Quoted rather than {@code '$.' || attr_key}, because the keys this has to survive contain
     * dots: {@code cache.hit} under an unquoted path resolves to a nested object named {@code hit}
     * that no recording has, and the value silently reads as null. Keys containing a double quote
     * are dropped by the derivation instead, being unrepresentable here.
     */
    static final String KEY_PATH = "'$.\"' || attr_key || '\"'";

    private TraceAttributeQueries() {
    }

    /**
     * One predicate per condition, in order, each matching a single row of the attribute index.
     *
     * @param conditions what has to hold
     * @param params     bound with each condition's value, under a name derived from its position
     */
    static List<String> predicates(
            List<TraceAttributeCondition> conditions, MapSqlParameterSource params) {

        List<String> predicates = new ArrayList<>(conditions.size());
        for (int i = 0; i < conditions.size(); i++) {
            TraceAttributeCondition condition = conditions.get(i);
            TraceAttributeKeyId key = condition.key();

            String keyParam = "attr_key_" + i;
            params.addValue(keyParam, key.key());

            // The owner is spelled into the SQL rather than bound, because half the time it is NULL
            // and an untyped NULL parameter in a comparison is exactly the thing that behaves
            // differently across engines. It is an event type name from the catalog, never a value
            // a caller composed.
            String ownerClause = key.owner() == null
                    ? "owner IS NULL"
                    : "owner = :attr_owner_" + i;
            if (key.owner() != null) {
                params.addValue("attr_owner_" + i, key.owner());
            }

            String valueClause = condition.operator().predicate("attr_value_" + i);
            if (condition.operator().needsValue()) {
                params.addValue("attr_value_" + i, condition.value());
            }

            predicates.add("(source = '%s' AND %s AND attr_key = :%s AND %s)"
                    .formatted(key.source().name(), ownerClause, keyParam, valueClause));
        }
        return predicates;
    }

    /**
     * The trace ids matching every condition, as a subquery.
     * <p>
     * The scope is the entire difference between "these conditions held somewhere in the trace" and
     * "they held together on one span", and it is one clause: group by the trace and each condition
     * may be satisfied by a different span, group by the span as well and they may not.
     *
     * @return the subquery, or {@code null} when nothing narrows the result
     */
    static String matchingTraces(TraceAttributeSearchQuery query, MapSqlParameterSource params) {
        if (query.isUnfiltered()) {
            return null;
        }

        List<String> having = predicates(query.conditions(), params).stream()
                .map(predicate -> "COUNT(*) FILTER (WHERE %s) > 0".formatted(predicate))
                .toList();

        return """
                SELECT trace_id
                FROM trace_span_attributes
                GROUP BY %s
                HAVING %s""".formatted(query.scope().grouping(), String.join("\n   AND ", having));
    }

    /**
     * The {@code WHERE} narrowing a read of {@code traces} to the matches, or an empty string when
     * the query narrows nothing.
     *
     * @param alias how {@code traces} is aliased in the statement, or {@code null} when it is not
     */
    static String traceFilter(TraceAttributeSearchQuery query, String alias, MapSqlParameterSource params) {
        String matching = matchingTraces(query, params);
        if (matching == null) {
            return "";
        }
        String column = alias == null ? "trace_id" : alias + ".trace_id";
        return "WHERE %s IN (%s)".formatted(column, matching);
    }

    /**
     * The {@code ORDER BY} for a trace list. The column never comes from a caller — it is an enum
     * constant's own spelling — and the tie-break keeps a row at the page boundary from appearing
     * on two pages or on neither.
     */
    static String orderBy(String column, boolean descending) {
        return "%s %s, trace_id".formatted(column, descending ? "DESC" : "ASC");
    }
}
