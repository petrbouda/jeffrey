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
package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.common.treetable.EventViewerData;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.shared.common.model.Type;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Map;

/**
 * What the fields of one JFR event type mean.
 * <p>
 * {@code jfr_describeTable} describes the DuckDB tables, and for {@code events} the answer is a
 * {@code fields} column holding JSON — true, and useless for writing a query. The fields inside it
 * differ per event type, and their names are the JFR ones rather than anything Jeffrey chose. Without
 * this a reader either guesses them or selects a row to look at, and guessing a JFR field name is
 * reliably wrong: it is {@code sumOfPauses} rather than {@code duration}, {@code allocationSize}
 * rather than {@code size}.
 * <p>
 * Registered under the {@code jfr} prefix beside the DuckDB tools, because it belongs to the same
 * question — how do I query this profile — and a reader looking for it will look there.
 */
public class EventTypeMcpTools {

    private static final String EVENT_TYPES_VIEW = "event-types";
    private static final String EVENTS_VIEW = "events";
    private static final String EVENT_TYPE_PARAM = "eventType";

    private static final String NO_SUCH_EVENT_TYPE =
            "This profile recorded no event type called '%s'. jfr_listEventTypes names the ones it has, "
                    + "with their counts.";

    private static final String STEP_QUERY =
            "jfr_queryEvents with this eventType returns rows; the field names above are the keys inside "
                    + "each row's JSON 'fields' column.";
    private static final String STEP_SQL =
            "In SQL, read one field with fields->>'name'. jfr_executeQuery runs it.";

    private final ProfileManager profileManager;

    public EventTypeMcpTools(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    @Tool(description = "The fields of one JFR event type, with their labels and types — what is "
            + "actually inside the JSON 'fields' column of the events table for that type. Call it "
            + "before writing a query against an event type you have not queried before: field names "
            + "are JFR's rather than Jeffrey's, and guessing them is how a query comes back empty for "
            + "a recording that holds the data. jfr_listEventTypes names the types this profile has.")
    public String describeEventType(
            @ToolParam(required = true, description = "The event type, e.g. 'jdk.ObjectAllocationSample' "
                    + "or 'jdk.GarbageCollection', as jfr_listEventTypes reports it")
            String eventType) {

        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException(
                    "eventType is required. Call jfr_listEventTypes to see what this profile recorded.");
        }
        String code = eventType.trim();
        EventViewerData recorded = profileManager.eventViewerManager().eventTypes().stream()
                .filter(candidate -> candidate.code().equals(code))
                .findFirst()
                .orElse(null);
        if (recorded == null) {
            return McpToolOutput.error(NO_SUCH_EVENT_TYPE.formatted(code));
        }

        List<Field> fields = profileManager.eventViewerManager()
                .eventColumns(Type.fromCode(code)).stream()
                .map(field -> new Field(field.field(), field.header(), field.type(), field.description()))
                .toList();

        return LinkedOutput.json(new EventTypeDetail(
                recorded.code(),
                recorded.name(),
                recorded.categories(),
                recorded.count(),
                recorded.withStackTrace(),
                fields,
                List.of(STEP_QUERY, STEP_SQL),
                UiLinks.view(profileManager.info().id(), EVENTS_VIEW,
                        Map.of(EVENT_TYPE_PARAM, code))));
    }

    /**
     * @param withStackTrace whether events of this type carry a stack, which decides whether it can be
     *                       drawn as a flamegraph at all
     */
    private record EventTypeDetail(
            String eventType,
            String label,
            List<String> categories,
            long count,
            boolean withStackTrace,
            List<Field> fields,
            List<String> nextSteps,
            String uiLink) {
    }

    private record Field(String name, String label, String type, String description) {
    }
}
