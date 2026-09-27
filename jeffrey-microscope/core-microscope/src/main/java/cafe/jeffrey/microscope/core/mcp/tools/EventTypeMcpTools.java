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

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.common.treetable.EventViewerData;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
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

    private static final MicroscopeView EVENTS_VIEW = MicroscopeView.EVENTS;
    private static final String EVENT_TYPE_PARAM = "eventType";

    private static final String NO_SUCH_EVENT_TYPE =
            "This profile recorded no event type called '%s'. jfr_listEventTypes names the ones it has, "
                    + "with their counts.";

    private static final String QUERY_EVENTS = "jfr_queryEvents";
    private static final String PROFILE_ID = "profileId";
    private static final String EVENT_TYPE = "eventType";
    private static final String LIMIT = "limit";
    private static final int SAMPLE_EVENTS = 10;
    private static final String QUERY_WHY =
            "reads the latest events of this type; the field names are the keys inside each row's JSON fields column";
    private static final String SQL_GUIDANCE =
            "In SQL, read one field with fields->>'name'; jfr_executeQuery runs it.";

    private final ProfileManager profileManager;
    private final AdvertisedFamilies advertised;

    public EventTypeMcpTools(ProfileManager profileManager, AdvertisedFamilies advertised) {
        this.profileManager = profileManager;
        this.advertised = advertised;
    }

    @Tool(description = "Describes the fields of one JFR event type, with their labels and types - what "
            + "the JSON 'fields' column of the events table actually holds for that type. Field "
            + "names are JFR's rather than Jeffrey's, so a query with a guessed name comes back "
            + "empty for a recording that holds the data. jfr_listEventTypes names the types this "
            + "profile has; a type it did not record is an error naming it.")
    @McpOutputSchema(EventTypeDetail.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult describeEventType(
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
            throw new IllegalArgumentException(NO_SUCH_EVENT_TYPE.formatted(code));
        }

        List<Field> fields = profileManager.eventViewerManager()
                .eventColumns(Type.fromCode(code)).stream()
                .map(field -> new Field(field.field(), field.header(), field.type(), field.description()))
                .toList();

        String profileId = profileManager.info().id();
        McpFollowUp followUp = NextSteps.builder(advertised)
                .nextWhen(recorded.count() > 0, McpNextTool.call(QUERY_EVENTS).with(PROFILE_ID, profileId)
                        .with(EVENT_TYPE, code).with(LIMIT, SAMPLE_EVENTS).why(QUERY_WHY))
                .guidance(SQL_GUIDANCE)
                .followUp();
        return McpToolResult.of(new EventTypeDetail(
                recorded.code(),
                recorded.name(),
                recorded.categories(),
                recorded.count(),
                recorded.withStackTrace(),
                fields,
                followUp,
                UiLinks.view(profileId, EVENTS_VIEW, Map.of(EVENT_TYPE_PARAM, code))));
    }

    /**
     * @param withStackTrace whether events of this type carry a stack, which decides whether it can be
     *                       drawn as a flamegraph at all
     */
    record EventTypeDetail(
            String eventType,
            String label,
            List<String> categories,
            @McpDescription("How many events of this type the profile holds")
            long count,
            boolean withStackTrace,
            List<Field> fields,
            McpFollowUp followUp,
            @McpDescription("The event viewer on this event type in the Microscope UI, for the user")
            String uiLink) {
    }

    record Field(
            @McpDescription("The key inside the events view's JSON fields column")
            String name,
            @McpNullable
            @McpDescription("The field's label; null when the recording declared none")
            String label,
            @McpNullable
            @McpDescription("The field's JFR type; null when the recording declared none")
            String type,
            @McpNullable
            String description) {
    }
}
