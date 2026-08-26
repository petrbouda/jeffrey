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

import cafe.jeffrey.provider.profile.api.TraceAttributeCarrier;
import cafe.jeffrey.provider.profile.api.TraceAttributeCondition;
import cafe.jeffrey.provider.profile.api.TraceAttributeKeyId;
import cafe.jeffrey.provider.profile.api.TraceAttributeOperator;
import cafe.jeffrey.provider.profile.api.TraceAttributeScope;
import cafe.jeffrey.provider.profile.api.TraceAttributeSearchQuery;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    /** The two index tables, one per carrier. Constants of this class, never caller text. */
    static final String SPAN_ATTRIBUTES_TABLE = "trace_span_attributes";
    static final String NOTIFICATION_ATTRIBUTES_TABLE = "trace_notification_attributes";

    private TraceAttributeQueries() {
    }

    /**
     * The predicates of one search, split by the carrier their keys belong to.
     * <p>
     * They are split rather than listed together because the two live in different tables and are
     * grouped by different keys. A search naming only spans therefore produces exactly the SQL it
     * always did — one grouped scan of one table — and pays nothing for notifications existing.
     *
     * @param spans         predicates over {@code trace_span_attributes}
     * @param notifications predicates over {@code trace_notification_attributes}
     */
    record Predicates(List<String> spans, List<String> notifications) {

        boolean isEmpty() {
            return spans.isEmpty() && notifications.isEmpty();
        }

        /** Every predicate, for the hit lookup, which ORs them across rows of one table. */
        List<String> of(TraceAttributeCarrier carrier) {
            return switch (carrier) {
                case SPAN -> spans;
                case NOTIFICATION -> notifications;
            };
        }
    }

    /**
     * One predicate per condition, each matching a single row of its carrier's attribute index.
     * <p>
     * The parameter names are derived from a condition's position in the original list, not from its
     * position within its carrier's group, so splitting the conditions cannot make two of them
     * collide on one bound name.
     *
     * @param conditions what has to hold
     * @param params     bound with each condition's value, under a name derived from its position
     */
    static Predicates predicates(
            List<TraceAttributeCondition> conditions, MapSqlParameterSource params) {

        List<String> spanPredicates = new ArrayList<>();
        List<String> notificationPredicates = new ArrayList<>();
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

            String valueClause = valueClause(condition.operator(), "attr_value_" + i);
            if (condition.operator().needsValue()) {
                params.addValue("attr_value_" + i, condition.value());
            }

            String predicate = "(source = '%s' AND %s AND attr_key = :%s AND %s)"
                    .formatted(key.source().name(), ownerClause, keyParam, valueClause);

            switch (key.source().carrier()) {
                case SPAN -> spanPredicates.add(predicate);
                case NOTIFICATION -> notificationPredicates.add(predicate);
            }
        }
        return new Predicates(List.copyOf(spanPredicates), List.copyOf(notificationPredicates));
    }

    /**
     * The value half of one condition's predicate. A text comparison runs against the value
     * dictionary — the index stores only references — and comes back as a membership test over the
     * references whose text matches; with a few thousand dictionary rows the lookup is a scan of
     * nothing. Numeric comparisons and {@code EXISTS} read the index row itself, as before.
     */
    private static String valueClause(TraceAttributeOperator operator, String parameter) {
        if (!operator.readsText()) {
            return operator.predicate(parameter);
        }
        return "value_id IN (SELECT value_id FROM trace_attribute_values WHERE %s)"
                .formatted(operator.predicate(parameter));
    }

    /**
     * The trace ids matching every condition, as a subquery.
     * <p>
     * The scope is the entire difference between "these conditions held somewhere in the trace" and
     * "they held together on one carrier", and it is one clause: group by the trace and each
     * condition may be satisfied by a different carrier, group by the carrier as well and they may
     * not.
     * <p>
     * Conditions over both carriers become one branch each, combined with {@code INTERSECT}. That is
     * what AND across conditions means once they live in tables grouped by different keys, and it
     * says so directly rather than through a join the reader has to decode. A search naming one
     * carrier emits its branch alone, so the common case is the statement it always was.
     *
     * @return the subquery, or {@code null} when nothing narrows the result
     */
    static String matchingTraces(TraceAttributeSearchQuery query, MapSqlParameterSource params) {
        if (query.isUnfiltered()) {
            return null;
        }

        Predicates predicates = predicates(query.conditions(), params);
        if (predicates.isEmpty()) {
            return null;
        }

        List<String> branches = new ArrayList<>(2);
        for (TraceAttributeCarrier carrier : TraceAttributeCarrier.values()) {
            List<String> carrierPredicates = predicates.of(carrier);
            if (!carrierPredicates.isEmpty()) {
                branches.add(branch(carrier, carrierPredicates, query.scope()));
            }
        }
        return String.join("\nINTERSECT\n", branches);
    }

    /** One carrier's half of the match: the traces where every condition of that carrier held. */
    private static String branch(
            TraceAttributeCarrier carrier, List<String> predicates, TraceAttributeScope scope) {

        String having = predicates.stream()
                .map(predicate -> "COUNT(*) FILTER (WHERE %s) > 0".formatted(predicate))
                .collect(Collectors.joining("\n   AND "));

        return """
                SELECT trace_id
                FROM %s
                GROUP BY %s
                HAVING %s""".formatted(table(carrier), scope.grouping(carrier), having);
    }

    /**
     * The index table one carrier's rows live in. Derived from the carrier rather than passed in, so
     * a predicate can never be run against the other carrier's table.
     */
    private static String table(TraceAttributeCarrier carrier) {
        return switch (carrier) {
            case SPAN -> SPAN_ATTRIBUTES_TABLE;
            case NOTIFICATION -> NOTIFICATION_ATTRIBUTES_TABLE;
        };
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
