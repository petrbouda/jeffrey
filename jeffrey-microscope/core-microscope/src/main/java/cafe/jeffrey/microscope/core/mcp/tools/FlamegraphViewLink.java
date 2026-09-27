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

import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.model.Type;

import java.util.Map;

/**
 * A link into the Microscope flamegraph view that draws the very graph an answer described: the event
 * type and flags, the window of the recording, the search, and - for a comparison - the baseline.
 * <p>
 * The parameter names are the view's own, read by {@code ProfileFlamegraphView} and
 * {@code FlamegraphLinkQuery} (window and search) and by {@code BaselineQuery} (baseline); they are
 * spelled here and nowhere else on this side, and are not the tools' argument names even where the
 * spelling happens to agree. A window travels as UTC epoch milliseconds - the base every answer uses -
 * and the view places it on the recording's start itself. A flag travels only when it is on, the way
 * the view reads it.
 */
final class FlamegraphViewLink {

    private static final MicroscopeView GRAPH_VIEW = MicroscopeView.FLAMEGRAPH_VIEW;
    private static final MicroscopeView PAIR_GRID_VIEW = MicroscopeView.FLAMEGRAPHS_DIFFERENTIAL;

    private static final String EVENT_TYPE_PARAM = "eventType";
    private static final String GRAPH_MODE_PARAM = "graphMode";
    private static final String PRIMARY_GRAPH_MODE = "PRIMARY";
    private static final String DIFFERENTIAL_GRAPH_MODE = "DIFFERENTIAL";
    private static final String BASELINE_PARAM = "baseline";
    private static final String USE_WEIGHT_PARAM = "useWeight";
    private static final String USE_THREAD_MODE_PARAM = "useThreadMode";
    private static final String EXCLUDE_IDLE_PARAM = "excludeIdleSamples";
    private static final String EXCLUDE_NON_JAVA_PARAM = "excludeNonJavaSamples";
    private static final String START_EPOCH_MS_PARAM = "startEpochMs";
    private static final String END_EPOCH_MS_PARAM = "endEpochMs";
    private static final String SEARCH_PARAM = "search";

    private final String profileId;
    private final Map<String, String> query = UiLinks.query();

    private FlamegraphViewLink(String profileId, Type type, String graphMode) {
        this.profileId = profileId;
        query.put(EVENT_TYPE_PARAM, type.code());
        query.put(GRAPH_MODE_PARAM, graphMode);
    }

    /** One profile's flamegraph of this event type. */
    static FlamegraphViewLink primary(String profileId, Type type) {
        return new FlamegraphViewLink(profileId, type, PRIMARY_GRAPH_MODE);
    }

    /** The differential flamegraph of this event type, the primary measured against the baseline. */
    static FlamegraphViewLink differential(String profileId, String baselineProfileId, Type type) {
        FlamegraphViewLink link = new FlamegraphViewLink(profileId, type, DIFFERENTIAL_GRAPH_MODE);
        link.query.put(BASELINE_PARAM, baselineProfileId);
        return link;
    }

    /** The differential grid of a pair: every event type the two can be compared on. */
    static String pair(String profileId, String baselineProfileId) {
        Map<String, String> query = UiLinks.query();
        query.put(BASELINE_PARAM, baselineProfileId);
        return UiLinks.view(profileId, PAIR_GRID_VIEW, query);
    }

    FlamegraphViewLink weighted(boolean weighted) {
        query.put(USE_WEIGHT_PARAM, UiLinks.flag(weighted));
        return this;
    }

    FlamegraphViewLink threadMode(boolean threadMode) {
        query.put(USE_THREAD_MODE_PARAM, UiLinks.flag(threadMode));
        return this;
    }

    FlamegraphViewLink excludeIdle(boolean excludeIdle) {
        query.put(EXCLUDE_IDLE_PARAM, UiLinks.flag(excludeIdle));
        return this;
    }

    FlamegraphViewLink excludeNonJava(boolean excludeNonJava) {
        query.put(EXCLUDE_NON_JAVA_PARAM, UiLinks.flag(excludeNonJava));
        return this;
    }

    /**
     * The window the view opens on; {@code null} leaves it to the view, which opens on its first hour.
     */
    FlamegraphViewLink window(EpochWindow window) {
        if (window != null) {
            query.put(START_EPOCH_MS_PARAM, String.valueOf(window.startEpochMs()));
            query.put(END_EPOCH_MS_PARAM, String.valueOf(window.endEpochMs()));
        }
        return this;
    }

    /** The search the view runs once the graph is drawn; a blank one is left out. */
    FlamegraphViewLink search(String search) {
        query.put(SEARCH_PARAM, search);
        return this;
    }

    String url() {
        return UiLinks.view(profileId, GRAPH_VIEW, query);
    }
}
