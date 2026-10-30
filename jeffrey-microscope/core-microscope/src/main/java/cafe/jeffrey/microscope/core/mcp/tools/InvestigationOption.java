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
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolWeight;

import java.util.List;

/**
 * One line of the investigation menu: an area the user can choose, what it answers and what it costs
 * the conversation, whether the evidence points at it, and the calls that open it — for an area whose
 * data has to be built first, what is not built and the call that builds it — or, for an area the
 * profile cannot answer, what is missing and what would capture it next time.
 */
record InvestigationOption(
        @McpDescription("The area, by name; choosing it means sending its nextTools unchanged")
        InvestigationArea area,
        @McpDescription("Which group the area is offered in; a profile is offered at most four")
        InvestigationGroup group,
        @McpDescription("A short name for the area, for the user")
        String title,
        @McpDescription("What investigating the area answers, in one clause")
        String question,
        @McpDescription("AVAILABLE when this profile can answer it now; NEEDS_PREPARATION when its data is "
                + "there but has to be built first (a heap dump's index or report), by the nextTools; "
                + "NOT_RECORDED when the profile lacks its data")
        Availability availability,
        @McpNullable
        @McpDescription("The heaviest of its nextTools; null when it has none")
        McpToolWeight weight,
        @McpDescription("Whether the evidence points here: a fired rule, or the dominant kind of sample")
        boolean suggested,
        @McpNullable
        @McpDescription("The finding or figure that makes it suggested; null when it is not")
        String evidence,
        @McpNullable
        @McpDescription("What the profile is missing, or what is not built yet; null when the area is available")
        String gap,
        @McpNullable
        @McpDescription("What would record it next time, or how it gets built; null when available or when "
                + "nothing short of different instrumentation would")
        String remedy,
        @McpDescription("The calls that open the area, or that build its data first, ready to send; empty "
                + "when it is not recorded")
        List<McpNextTool> nextTools) {

    enum Availability {
        AVAILABLE,
        NEEDS_PREPARATION,
        NOT_RECORDED
    }

    InvestigationOption {
        nextTools = List.copyOf(nextTools);
        boolean available = availability == Availability.AVAILABLE;
        boolean recorded = availability != Availability.NOT_RECORDED;
        if (recorded == nextTools.isEmpty() || recorded == (weight == null)) {
            throw new IllegalArgumentException("an area has calls and their weight exactly when its data is "
                    + "recorded: area=" + area + " calls=" + nextTools.size());
        }
        if (available == (gap != null)) {
            throw new IllegalArgumentException(
                    "an available area has no gap, any other names one: area=" + area);
        }
        if (suggested != (evidence != null) || (suggested && !available)) {
            throw new IllegalArgumentException(
                    "a suggested area is available and names its evidence: area=" + area);
        }
    }

    /** The option for an area this profile can answer, with the calls that open it. */
    static InvestigationOption available(InvestigationArea area, List<McpNextTool> calls) {
        return new InvestigationOption(area, area.group(), area.title(), area.question(),
                Availability.AVAILABLE, heaviest(area, calls), false, null, null, null, calls);
    }

    /** The option for an area whose data the profile holds but has to be built first, by these calls. */
    static InvestigationOption needsPreparation(InvestigationArea area, AreaSource.Preparation preparation) {
        return new InvestigationOption(area, area.group(), area.title(), area.question(),
                Availability.NEEDS_PREPARATION, heaviest(area, preparation.calls()), false, null,
                preparation.gap(), preparation.remedy(), preparation.calls());
    }

    /** The option for an area the recording cannot answer. */
    static InvestigationOption notRecorded(InvestigationArea area, String gap, String remedy) {
        return new InvestigationOption(area, area.group(), area.title(), area.question(),
                Availability.NOT_RECORDED, null, false, null, gap, remedy, List.of());
    }

    /** The same option, pointed at by the evidence. */
    InvestigationOption suggestedBy(String evidence) {
        return new InvestigationOption(area, group, title, question, availability, weight, true, evidence,
                gap, remedy, nextTools);
    }

    boolean isAvailable() {
        return availability == Availability.AVAILABLE;
    }

    private static McpToolWeight heaviest(InvestigationArea area, List<McpNextTool> calls) {
        return calls.stream()
                .map(McpNextTool::weight)
                .max(Enum::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("an area with data needs a call: area=" + area));
    }
}
