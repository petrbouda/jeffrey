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
