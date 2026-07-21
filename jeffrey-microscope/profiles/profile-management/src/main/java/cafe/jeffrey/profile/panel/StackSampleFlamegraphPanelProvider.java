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

import java.util.ArrayList;
import java.util.List;

/**
 * The flamegraph grid for aggregated stack-sample formats (pprof / OTLP): one plain card per stored sample
 * dimension, titled with the event code verbatim. These formats are pre-aggregated and self-describing, so
 * there is no category classification and no per-thread data — the only per-card variation (weight
 * formatting) comes from the sample unit. The profile's format is already known by the caller, so it is not
 * repeated on every card.
 */
public final class StackSampleFlamegraphPanelProvider implements FlamegraphPanelProvider {

    @Override
    public List<FlamegraphPanel> panels(List<EventSummaryResult> summaries, PanelContext context) {
        List<FlamegraphPanel> panels = new ArrayList<>(summaries.size());
        int order = 0;
        for (EventSummaryResult event : summaries) {
            panels.add(PanelAssembler.stackSample(event, order));
            order++;
        }
        return panels;
    }
}
