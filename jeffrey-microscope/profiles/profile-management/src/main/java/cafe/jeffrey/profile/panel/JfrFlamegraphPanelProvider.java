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

package cafe.jeffrey.profile.panel;

import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.profile.model.FlamegraphPanel;
import cafe.jeffrey.shared.common.model.Type;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The JFR flamegraph grid: the full set of eight standard sections in order, with a greyed zero-sample
 * placeholder panel for any section the profile has no samples for (so the grid always shows the complete
 * catalog). Each summary is routed to its section server-side via {@link Type}, replacing the old
 * frontend code predicates.
 */
public final class JfrFlamegraphPanelProvider implements FlamegraphPanelProvider {

    @Override
    public List<FlamegraphPanel> panels(List<EventSummaryResult> summaries, PanelContext context) {
        Map<PanelSection, List<EventSummaryResult>> bySection = new EnumMap<>(PanelSection.class);
        for (EventSummaryResult summary : summaries) {
            PanelSection section = sectionFor(summary.code());
            if (section != null) {
                bySection.computeIfAbsent(section, key -> new ArrayList<>()).add(summary);
            }
        }

        List<FlamegraphPanel> panels = new ArrayList<>();
        for (PanelSection section : PanelSection.values()) {
            List<EventSummaryResult> matched = bySection.get(section);
            if (matched == null || matched.isEmpty()) {
                panels.add(assemble(section, placeholder(section), context));
            } else {
                for (EventSummaryResult event : matched) {
                    panels.add(assemble(section, event, context));
                }
            }
        }
        return panels;
    }

    private static FlamegraphPanel assemble(PanelSection section, EventSummaryResult event, PanelContext context) {
        return PanelAssembler.assemble(section, event, section.title(), context, false, true);
    }

    private static PanelSection sectionFor(String code) {
        Type type = Type.fromCode(code);
        if (Type.EXECUTION_SAMPLE.equals(type)) {
            return PanelSection.EXECUTION;
        }
        if (Type.CPU_TIME_SAMPLE.equals(type)) {
            return PanelSection.CPU_TIME;
        }
        if (Type.METHOD_TRACE.equals(type)) {
            return PanelSection.METHOD;
        }
        if (Type.WALL_CLOCK_SAMPLE.equals(type)) {
            return PanelSection.WALL;
        }
        if (Type.MALLOC.equals(type)) {
            return PanelSection.NATIVE_ALLOC;
        }
        if (Type.NATIVE_LEAK.equals(type)) {
            return PanelSection.NATIVE_LEAK;
        }
        if (type.isAllocationEvent()) {
            return PanelSection.ALLOCATION;
        }
        if (type.isBlockingEvent()) {
            return PanelSection.BLOCKING;
        }
        return null;
    }

    private static EventSummaryResult placeholder(PanelSection section) {
        String code = section.placeholderCode();
        String label = section.placeholderLabel();
        EventSummaryResult.SingleResult primary =
                new EventSummaryResult.SingleResult(code, label, null, null, 0L, 0L, false, Map.of());
        return new EventSummaryResult(code, label, primary, null);
    }
}
