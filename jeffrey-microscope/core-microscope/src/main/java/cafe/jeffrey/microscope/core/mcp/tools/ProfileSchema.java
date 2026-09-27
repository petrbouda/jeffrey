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

import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;

import java.util.List;

/**
 * The profile database as a query sees it: the document {@code jeffrey://profile/{profileId}/schema}
 * serves.
 * <p>
 * Bounded by what the schema and the recording declare: the fixed set of tables and views with their
 * columns, and one entry per declared event type -- about 15 KB on a real profile.
 *
 * @param relations  every table and view a query can read, the {@code events} view included
 * @param eventTypes every event type the recording declared, with how many events of it are stored
 * @param uiLink     the profile's Event Types page in Microscope, for the user
 */
public record ProfileSchema(
        String profileId,
        List<Relation> relations,
        List<EventType> eventTypes,
        @McpDescription("The profile's Event Types page in the Microscope UI, for the user")
        String uiLink) {

    /**
     * @param view  whether it is a view rather than a stored table
     * @param note  what to know before querying it, or null when there is nothing
     */
    public record Relation(
            String name,
            @McpDescription("Whether this is a view rather than a stored table")
            boolean view,
            List<Column> columns,
            @McpNullable
            @McpDescription("What to know before querying it; null when there is nothing")
            String note) {
    }

    /**
     * @param type the DuckDB type, e.g. {@code BIGINT}, {@code VARCHAR}, {@code JSON}
     */
    public record Column(String name, String type, boolean nullable) {
    }

    /**
     * @param count   how many events of this type the database stores; zero for a declared type the
     *                recording holds none of
     * @param samples the samples those events carry
     */
    public record EventType(
            String name,
            String label,
            @McpNullable
            String description,
            long count,
            long samples) {
    }
}
