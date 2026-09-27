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


package cafe.jeffrey.microscope.core.mcp.tools.heap;

import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpNullableElement;
import cafe.jeffrey.profile.mcp.McpFollowUp;

import java.util.List;

import static cafe.jeffrey.microscope.core.mcp.tools.heap.HeapOverviewAnswers.FOLLOW_UP;
import static cafe.jeffrey.microscope.core.mcp.tools.heap.HeapOverviewAnswers.UI_LINK;

/**
 * The answers of the tools that read the heap-dump index as tables. The index's tables have no page in
 * the Microscope UI, so these carry no link; the dump's metadata is shown on the overview page and
 * links there.
 */
public final class HeapSqlAnswers {

    private HeapSqlAnswers() {
    }

    /** Whether the dump recorded its parser metadata. */
    public enum MetadataStatus {
        OK,
        /** The index holds no metadata row, as an index built by an older parser may not. */
        NO_METADATA
    }

    public record Tables(
            String profileId,
            @McpDescription("The tables of the heap-dump index database, by name")
            List<String> tables,
            @McpDescription(FOLLOW_UP)
            McpFollowUp followUp) {
    }

    public record TableColumns(
            String profileId,
            String table,
            @McpDescription("The table's columns in declaration order")
            List<Column> columns,
            @McpDescription(FOLLOW_UP)
            McpFollowUp followUp) {
    }

    public record Column(String name, String dataType, boolean nullable) {
    }

    public record QueryResult(
            String profileId,
            List<String> columns,
            @McpDescription("One array per row, its cells in column order as text; a SQL NULL is null")
            List<List<@McpNullableElement String>> rows,
            @McpDescription("The most rows any query returns")
            int rowCap,
            @McpDescription("Whether the query matched more rows than rowCap; tighten the WHERE clause or aggregate")
            boolean capped,
            @McpDescription(FOLLOW_UP)
            McpFollowUp followUp) {
    }

    public record DumpMetadata(
            MetadataStatus status,
            @McpNullable
            @McpDescription("Why there is no metadata; null when it was read")
            String reason,
            String profileId,
            @McpNullable
            Metadata metadata,
            @McpDescription(FOLLOW_UP)
            McpFollowUp followUp,
            @McpDescription(UI_LINK)
            String uiLink) {
    }

    public record Metadata(
            @McpNullable
            @McpDescription("Bytes per object id: 4 or 8")
            Integer idSizeBytes,
            @McpNullable
            String hprofVersion,
            @McpNullable
            Boolean compressedOops,
            @McpNullable
            Long bytesParsed,
            @McpNullable
            Long recordCount,
            @McpNullable
            Long warningCount,
            @McpNullable
            @McpDescription("Whether the dump file ended early")
            Boolean truncated,
            @McpNullable
            String parserVersion,
            @McpNullable
            @McpDescription("When the dump was parsed, as UTC epoch milliseconds")
            Long parsedAtEpochMs) {
    }
}
