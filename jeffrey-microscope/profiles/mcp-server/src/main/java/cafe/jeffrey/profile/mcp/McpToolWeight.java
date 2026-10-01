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
package cafe.jeffrey.profile.mcp;

import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import tools.jackson.databind.JsonNode;

/**
 * What one call's answer puts into the reader's conversation — the figure a user weighs when
 * choosing the next step, and the one {@link McpToolCost} does not give: a cheap call can still
 * answer with a long document, and a slow one with a few lines.
 * <p>
 * Never declared on its own: {@link #of} reads it from what a tool already declares in
 * {@link McpToolMeta}, so it cannot drift from the size and cost the tool advertises.
 */
public enum McpToolWeight {

    /** One or two compact records: a catalogue, a status, a link, a stored total. */
    LIGHT,

    /** A dashboard or a ranking: a bounded set of rows. */
    MEDIUM,

    /** A long document — a flamegraph, a trace, a dump, a query result — tens of thousands of characters. */
    HEAVY;

    /**
     * The weight a tool's own hints imply: heavy when it raises the size the host keeps inline,
     * light when its cost is {@link McpToolCost#CHEAP}, medium otherwise.
     */
    public static McpToolWeight of(McpToolMeta declared) {
        return of(declared.maxResultSizeChars() != McpToolMeta.NOT_DECLARED, declared.cost());
    }

    /**
     * The same reading of an advertised tool, from the {@code _meta} hints {@code tools/list} carries;
     * a tool without a cost hint is refused.
     */
    public static McpToolWeight of(McpToolSpec spec) {
        JsonNode cost = spec.meta().get(JeffreyMetaKeys.COST);
        if (cost == null || !cost.isString()) {
            throw new IllegalArgumentException("Tool advertises no cost hint: tool=" + spec.name());
        }
        return of(spec.meta().containsKey(JeffreyMetaKeys.MAX_RESULT_SIZE_CHARS),
                McpToolCost.valueOf(cost.asString()));
    }

    private static McpToolWeight of(boolean raisesInlineSize, McpToolCost cost) {
        if (raisesInlineSize) {
            return HEAVY;
        }
        if (cost == McpToolCost.CHEAP) {
            return LIGHT;
        }
        return MEDIUM;
    }
}
