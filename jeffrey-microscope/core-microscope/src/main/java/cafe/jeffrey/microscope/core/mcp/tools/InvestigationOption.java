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
 * the conversation, whether the evidence points at it, and the calls that open it — or, for an area
 * the recording cannot answer, what is missing and what would capture it next time.
 */
record InvestigationOption(
        @McpDescription("The area, by name; choosing it means sending its nextTools unchanged")
        InvestigationArea area,
        @McpDescription("Which of the four groups the area is offered in")
        InvestigationGroup group,
        @McpDescription("A short name for the area, for the user")
        String title,
        @McpDescription("What investigating the area answers, in one clause")
        String question,
        @McpDescription("AVAILABLE when this profile can answer it; NOT_RECORDED when the recording lacks its data")
        Availability availability,
        @McpNullable
        @McpDescription("The heaviest of its nextTools; null when the area is not available")
        McpToolWeight weight,
        @McpDescription("Whether the evidence points here: a fired rule, or the dominant kind of sample")
        boolean suggested,
        @McpNullable
        @McpDescription("The finding or figure that makes it suggested; null when it is not")
        String evidence,
        @McpNullable
        @McpDescription("What the recording is missing; null when the area is available")
        String gap,
        @McpNullable
        @McpDescription("What would record it next time; null when available or when nothing short of "
                + "different instrumentation would")
        String remedy,
        @McpDescription("The calls that open the area, ready to send; empty when it is not available")
        List<McpNextTool> nextTools) {

    enum Availability {
        AVAILABLE,
        NOT_RECORDED
    }

    InvestigationOption {
        nextTools = List.copyOf(nextTools);
        boolean available = availability == Availability.AVAILABLE;
        if (available == nextTools.isEmpty()) {
            throw new IllegalArgumentException(
                    "an area has calls exactly when it is available: area=" + area + " calls=" + nextTools.size());
        }
        if (available == (weight == null) || available == (gap != null)) {
            throw new IllegalArgumentException(
                    "an available area has a weight and no gap, any other the reverse: area=" + area);
        }
        if (suggested != (evidence != null) || (suggested && !available)) {
            throw new IllegalArgumentException(
                    "a suggested area is available and names its evidence: area=" + area);
        }
    }

    /** The option for an area this profile can answer, with the calls that open it. */
    static InvestigationOption available(InvestigationArea area, List<McpNextTool> calls) {
        McpToolWeight heaviest = calls.stream()
                .map(McpNextTool::weight)
                .max(Enum::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("an available area needs a call: area=" + area));
        return new InvestigationOption(area, area.group(), area.title(), area.question(),
                Availability.AVAILABLE, heaviest, false, null, null, null, calls);
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
}
