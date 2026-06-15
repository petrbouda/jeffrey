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

package cafe.jeffrey.microscope.core.guardian;

import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.profile.guardian.definition.GuardPreconditions;
import cafe.jeffrey.profile.guardian.definition.MatchExpr;
import cafe.jeffrey.profile.guardian.definition.MatchOp;
import cafe.jeffrey.profile.guardian.definition.TraversalStrategy;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates the persisted {@code matcher_spec} / {@code preconditions} JSON (see the
 * {@code guardians} table) into the typed {@link MatchExpr} / {@link TraversalStrategy} /
 * {@link GuardPreconditions} model. Hand-rolled with {@link JsonNode} so the domain records stay free
 * of Jackson annotations.
 */
public final class GuardSpecJson {

    private static final String FIELD_TYPE = "type";
    private static final String TYPE_PREDICATE = "Predicate";
    private static final String TYPE_ANY_OF = "AnyOf";
    private static final String TYPE_ALL_OF = "AllOf";
    private static final String TYPE_NOT = "Not";
    private static final String TYPE_CURRENT_FRAME = "CurrentFrame";
    private static final String TYPE_DESCEND = "Descend";
    private static final String STEP_BY_NAME = "ByName";
    private static final String STEP_BY_MATCHER = "ByMatcher";

    private GuardSpecJson() {
    }

    public record ParsedSpec(MatchExpr anchor, TraversalStrategy traversal) {
    }

    public static ParsedSpec parseSpec(String json) {
        JsonNode root = Json.readTree(json);
        MatchExpr anchor = parseExpr(root.get("anchor"));
        JsonNode traversalNode = root.get("traversal");
        TraversalStrategy traversal = traversalNode == null || traversalNode.isNull()
                ? TraversalStrategy.CURRENT_FRAME
                : parseTraversal(traversalNode);
        return new ParsedSpec(anchor, traversal);
    }

    private static MatchExpr parseExpr(JsonNode node) {
        String type = node.get(FIELD_TYPE).asString();
        return switch (type) {
            case TYPE_PREDICATE -> new MatchExpr.Predicate(
                    MatchOp.valueOf(node.get("op").asString()), node.get("value").asString());
            case TYPE_ANY_OF -> new MatchExpr.AnyOf(parseExprList(node.get("of")));
            case TYPE_ALL_OF -> new MatchExpr.AllOf(parseExprList(node.get("of")));
            case TYPE_NOT -> new MatchExpr.Not(parseExpr(node.get("expr")));
            default -> throw new IllegalArgumentException("Unknown MatchExpr type: " + type);
        };
    }

    private static List<MatchExpr> parseExprList(JsonNode arrayNode) {
        List<MatchExpr> expressions = new ArrayList<>();
        for (JsonNode child : arrayNode) {
            expressions.add(parseExpr(child));
        }
        return expressions;
    }

    private static TraversalStrategy parseTraversal(JsonNode node) {
        String type = node.get(FIELD_TYPE).asString();
        return switch (type) {
            case TYPE_CURRENT_FRAME -> TraversalStrategy.CURRENT_FRAME;
            case TYPE_DESCEND -> {
                List<TraversalStrategy.Step> steps = new ArrayList<>();
                for (JsonNode stepNode : node.get("steps")) {
                    steps.add(parseStep(stepNode));
                }
                yield new TraversalStrategy.Descend(steps);
            }
            default -> throw new IllegalArgumentException("Unknown TraversalStrategy type: " + type);
        };
    }

    private static TraversalStrategy.Step parseStep(JsonNode node) {
        String type = node.get(FIELD_TYPE).asString();
        return switch (type) {
            case STEP_BY_NAME -> new TraversalStrategy.Step.ByName(node.get("frameName").asString());
            case STEP_BY_MATCHER -> new TraversalStrategy.Step.ByMatcher(
                    parseExpr(node.get("base")), parseExpr(node.get("target")));
            default -> throw new IllegalArgumentException("Unknown Descend step type: " + type);
        };
    }

    public static GuardPreconditions parsePreconditions(String json) {
        if (json == null || json.isBlank()) {
            return GuardPreconditions.NONE;
        }
        JsonNode node = Json.readTree(json);
        RecordingEventSource eventSource = node.has("eventSource") && !node.get("eventSource").isNull()
                ? RecordingEventSource.valueOf(node.get("eventSource").asString()) : null;
        GarbageCollectorType gcType = node.has("garbageCollectorType") && !node.get("garbageCollectorType").isNull()
                ? GarbageCollectorType.valueOf(node.get("garbageCollectorType").asString()) : null;
        Boolean debugSymbols = node.has("debugSymbolsAvailable") && !node.get("debugSymbolsAvailable").isNull()
                ? node.get("debugSymbolsAvailable").asBoolean() : null;
        Boolean kernelSymbols = node.has("kernelSymbolsAvailable") && !node.get("kernelSymbolsAvailable").isNull()
                ? node.get("kernelSymbolsAvailable").asBoolean() : null;
        return new GuardPreconditions(eventSource, gcType, debugSymbols, kernelSymbols);
    }
}
