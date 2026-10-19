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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.shared.common.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Recognises the one DuckDB parse that turns an honest JSON filter into a baffling engine error.
 * <p>
 * DuckDB binds {@code ->} and {@code ->>} more loosely than {@code AND}, {@code OR}, comparisons,
 * arithmetic and {@code ::}. {@code event_type = 'x' AND fields->>'traceId' = 'y'} therefore reads as
 * {@code ((event_type = 'x' AND fields) ->> 'traceId') = 'y'}, and the engine answers "Failed to cast
 * value to numerical ... from source column fields" -- a message that names neither the operator nor
 * the fix, so a caller retries the same query. The same filter with its conditions in the other order
 * runs, which makes the trap harder still to see from the outside.
 * <p>
 * The check reads DuckDB's own parse tree ({@code json_serialize_sql}), never the text, and only after a
 * query has already failed: an arrow whose operand is a conjunction, a comparison or an operator is
 * never what anyone wrote, but a query that ran is left alone, so the check can never refuse a valid
 * one. A real lambda ({@code list_filter(l, x -> x > 1)}) parses to the same node as {@code ->}; it
 * always sits in a function's arguments with a bare parameter on its left, and only its left side is
 * judged there.
 */
final class JsonArrowPrecedence {

    private static final Logger LOG = LoggerFactory.getLogger(JsonArrowPrecedence.class);

    static final String HINT = "DuckDB binds -> and ->> more loosely than AND, OR, comparisons, arithmetic "
            + "and ::, so a filter like event_type = 'x' AND fields->>'key' = 'v' reads as "
            + "(event_type = 'x' AND fields)->>'key'. Wrap every extraction in parentheses -- "
            + "(fields->>'key') = 'v', (fields->>'size')::BIGINT -- or use json_extract_string(fields, '$.key').";

    /** The cast is required: the function refuses a parameter whose type it cannot see at prepare time. */
    private static final String SERIALIZE_SQL = "SELECT json_serialize_sql(?::VARCHAR)";

    private static final String ERROR = "error";
    private static final String CLASS = "class";
    private static final String FUNCTION_NAME = "function_name";
    private static final String IS_OPERATOR = "is_operator";
    private static final String CHILDREN = "children";
    private static final String LHS = "lhs";
    private static final String EXPR = "expr";

    private static final String FUNCTION_CLASS = "FUNCTION";
    private static final String LAMBDA_CLASS = "LAMBDA";
    private static final String CAST_CLASS = "CAST";

    /** {@code ->>} parses as an operator function; {@code ->} as a lambda, kept here in case that changes. */
    private static final Set<String> ARROWS = Set.of("->", "->>");

    /** Expressions that are never a JSON document nor a key: what an arrow swallowed from around it. */
    private static final Set<String> SWALLOWED_CLASSES = Set.of("CONJUNCTION", "COMPARISON", "OPERATOR");

    private JsonArrowPrecedence() {
    }

    /**
     * The hint for a query that failed because an arrow swallowed its neighbours, or empty when it did
     * not -- including when the query does not parse or the parse cannot be read, since this only ever
     * adds to an error that is already being reported.
     */
    static Optional<String> hintFor(DataSource dataSource, String sql) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SERIALIZE_SQL)) {
            stmt.setString(1, sql);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && misreads(Json.readTree(rs.getString(1)))) {
                    return Optional.of(HINT);
                }
                return Optional.empty();
            }
        } catch (SQLException | RuntimeException e) {
            LOG.debug("Could not read the parse tree of a failed query: message={}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Whether a {@code json_serialize_sql} tree holds an arrow that took a neighbour as its operand. */
    static boolean misreads(JsonNode parseTree) {
        if (parseTree.path(ERROR).asBoolean(false)) {
            return false;
        }
        return misreads(parseTree, false);
    }

    private static boolean misreads(JsonNode node, boolean functionArgument) {
        if (node.isArray()) {
            for (JsonNode element : node) {
                if (misreads(element, functionArgument)) {
                    return true;
                }
            }
            return false;
        }
        if (!node.isObject()) {
            return false;
        }
        if (swallowedNeighbour(node, functionArgument)) {
            return true;
        }
        boolean argumentsBelow = isPlainFunction(node);
        for (Map.Entry<String, JsonNode> property : node.properties()) {
            boolean arguments = argumentsBelow && CHILDREN.equals(property.getKey());
            if (misreads(property.getValue(), arguments)) {
                return true;
            }
        }
        return false;
    }

    private static boolean swallowedNeighbour(JsonNode node, boolean functionArgument) {
        String nodeClass = node.path(CLASS).asString("");
        if (FUNCTION_CLASS.equals(nodeClass) && ARROWS.contains(node.path(FUNCTION_NAME).asString(""))) {
            JsonNode operands = node.path(CHILDREN);
            return swallowedOnTheLeft(operands.path(0)) || swallowedOnTheRight(operands.path(1));
        }
        if (LAMBDA_CLASS.equals(nodeClass)) {
            return swallowedOnTheLeft(node.path(LHS)) || (!functionArgument && swallowedOnTheRight(node.path(EXPR)));
        }
        return false;
    }

    private static boolean swallowedOnTheLeft(JsonNode operand) {
        String operandClass = operand.path(CLASS).asString("");
        return SWALLOWED_CLASSES.contains(operandClass) || isNonArrowOperator(operand);
    }

    /** The key side also swallows a cast: {@code fields->>'size'::BIGINT} casts the key, not the value. */
    private static boolean swallowedOnTheRight(JsonNode operand) {
        return swallowedOnTheLeft(operand) || CAST_CLASS.equals(operand.path(CLASS).asString(""));
    }

    private static boolean isNonArrowOperator(JsonNode node) {
        return FUNCTION_CLASS.equals(node.path(CLASS).asString(""))
                && node.path(IS_OPERATOR).asBoolean(false)
                && !ARROWS.contains(node.path(FUNCTION_NAME).asString(""));
    }

    private static boolean isPlainFunction(JsonNode node) {
        return FUNCTION_CLASS.equals(node.path(CLASS).asString("")) && !node.path(IS_OPERATOR).asBoolean(false);
    }
}
