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

import cafe.jeffrey.microscope.mcp.protocol.McpMinimum;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.shared.common.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounds designated collections of an evidence document by dropping complete records, never fields
 * inside evidence, and says for each one how much it dropped and why.
 * <p>
 * The document is a record, built once its collections are bounded. So the budget starts from a
 * skeleton — the same document with every bounded collection empty — measured once, and each row then
 * costs what it serialises to, plus its separator, charged in the order the collections are bounded:
 * the first collection may spend the budget the later ones then report as exhausted. The estimate can
 * only stop early, never overrun, and {@link #METADATA_RESERVE} covers the truncation entries the
 * skeleton does not yet hold.
 */
final class EvidenceOutput {

    private static final int METADATA_RESERVE = 8_192;

    /** The comma a further row costs on top of its own JSON. */
    private static final int ROW_SEPARATOR_CHARS = 1;

    /** Why a collection holds fewer rows than it had. */
    enum TruncationReason {
        /** Nothing was dropped. */
        COMPLETE,
        /** The caller's row limit, or the tool's own, stopped it. */
        ROW_LIMIT,
        /** The response budget stopped it before the row limit did. */
        OUTPUT_SIZE_LIMIT
    }

    /**
     * What one bounded collection held and what it returned.
     *
     * @param total    rows the collection had
     * @param returned rows the document carries
     * @param omitted  rows dropped whole: {@code total - returned}
     * @param reason   why any were dropped
     */
    record Truncation(
            @McpMinimum(0)
            int total,
            @McpMinimum(0)
            int returned,
            @McpMinimum(0)
            int omitted,
            TruncationReason reason) {
    }

    private final int rowLimit;
    private final int budget;
    private final Map<String, Truncation> truncation = new LinkedHashMap<>();
    private int used;

    /**
     * @param skeleton the document with every collection this will bound still empty
     * @param rowLimit the most rows any one collection keeps
     */
    EvidenceOutput(Record skeleton, int rowLimit) {
        this.rowLimit = rowLimit;
        this.budget = McpToolOutput.MAX_CHARS - METADATA_RESERVE;
        this.used = Json.toString(skeleton).length();
    }

    /**
     * The leading rows of {@code values} that fit the row limit and what is left of the budget; the
     * counts are recorded under {@code path}, the collection's name in the document.
     */
    <T> List<T> rows(String path, List<T> values) {
        int count = Math.min(rowLimit, values.size());
        List<T> kept = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            T row = values.get(index);
            int cost = Json.toString(row).length() + ROW_SEPARATOR_CHARS;
            if (used + cost > budget) {
                break;
            }
            kept.add(row);
            used += cost;
        }
        TruncationReason reason = kept.size() == values.size() ? TruncationReason.COMPLETE
                : kept.size() < count ? TruncationReason.OUTPUT_SIZE_LIMIT : TruncationReason.ROW_LIMIT;
        truncation.put(path, new Truncation(values.size(), kept.size(), values.size() - kept.size(), reason));
        return Collections.unmodifiableList(kept);
    }

    /** Every collection bounded so far, by its path, in the order they were bounded. */
    Map<String, Truncation> truncation() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(truncation));
    }
}
