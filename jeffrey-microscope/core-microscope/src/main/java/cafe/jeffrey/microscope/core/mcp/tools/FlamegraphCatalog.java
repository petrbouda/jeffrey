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

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.model.FlamegraphPanel;
import cafe.jeffrey.profile.panel.FlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.PanelContext;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;

import java.util.List;

/**
 * The flamegraph grid of one profile, split into what it can graph and what the profiler never
 * captured.
 * <p>
 * The JFR provider emits the full catalog of standard sections, filling the ones this recording has
 * no samples for with a zero-sample placeholder so the frontend grid stays complete. Graphing a
 * placeholder yields an empty tree, so the split matters to two readers: {@code flamegraph_list},
 * which offers only the recorded ones as valid event types, and the profile summary, which reports
 * the missing ones as gaps in what the recording can answer. One class does the split so the two
 * cannot disagree about it.
 */
final class FlamegraphCatalog {

    private final ProfileManager profileManager;
    private final JfrFlamegraphPanelProvider jfrPanelProvider;
    private final StackSampleFlamegraphPanelProvider stackSamplePanelProvider;

    FlamegraphCatalog(
            ProfileManager profileManager,
            JfrFlamegraphPanelProvider jfrPanelProvider,
            StackSampleFlamegraphPanelProvider stackSamplePanelProvider) {

        this.profileManager = profileManager;
        this.jfrPanelProvider = jfrPanelProvider;
        this.stackSamplePanelProvider = stackSamplePanelProvider;
    }

    /**
     * Every panel of the grid, placeholders included, in the grid's own order.
     */
    List<FlamegraphPanel> panels() {
        return panelProvider().panels(profileManager.flamegraphManager().eventSummaries(), PanelContext.PRIMARY);
    }

    /**
     * The panels with samples behind them — the valid {@code eventType} values for an export.
     */
    List<FlamegraphPanel> recorded() {
        return panels().stream().filter(FlamegraphCatalog::isRecorded).toList();
    }

    /**
     * The standard groups this recording captured nothing for — the profiler was not configured to
     * collect them. Worth reporting to the reader; not valid {@code eventType} values.
     */
    List<FlamegraphPanel> notRecorded() {
        return panels().stream().filter(panel -> !isRecorded(panel)).toList();
    }

    static boolean isRecorded(FlamegraphPanel panel) {
        return panel.event().primary().samples() > 0;
    }

    /**
     * The grid this profile's format is drawn as — the same split the UI routes make. pprof and OTLP
     * carry their own sample dimensions rather than JFR event types, so running them through the JFR
     * catalog would report every dimension they do have as missing.
     */
    private FlamegraphPanelProvider panelProvider() {
        if (profileManager.info().eventSource().isFlamegraphOnlyImport()) {
            return stackSamplePanelProvider;
        }
        return jfrPanelProvider;
    }
}
