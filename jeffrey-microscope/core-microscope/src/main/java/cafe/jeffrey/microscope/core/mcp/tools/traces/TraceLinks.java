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

package cafe.jeffrey.microscope.core.mcp.tools.traces;

import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;

import java.util.Map;

/**
 * The Microscope pages a trace answer links to: one operation, and one trace's span waterfall. Held
 * in one place because every list of traces or operations links each of its rows, and a row must
 * open the same page whichever tool listed it.
 */
public final class TraceLinks {

    private static final MicroscopeView OPERATIONS_VIEW = MicroscopeView.TRACES_OPERATIONS;
    private static final MicroscopeView ATTRIBUTE_SEARCH_VIEW = MicroscopeView.TRACES_ATTRIBUTE_SEARCH;
    private static final String OPERATION_PARAM = "operation";
    private static final String KIND_PARAM = "kind";
    private static final String EVENT_TYPE_PARAM = "eventType";
    private static final String TAB_PARAM = "tab";
    private static final String TRACE_PARAM = "trace";

    private TraceLinks() {
    }

    /**
     * The operations page showing one operation, optionally on one of its tabs. All three parts of the
     * identity travel: a link carrying only the name would resolve to whichever of an inbound and an
     * outbound call of that name came first.
     *
     * @param tab the tab to open; null for the operation's summary
     */
    public static String operation(String profileId, String name, String kind, String eventType, String tab) {
        Map<String, String> query = UiLinks.query();
        query.put(OPERATION_PARAM, name);
        query.put(KIND_PARAM, kind);
        query.put(EVENT_TYPE_PARAM, eventType);
        query.put(TAB_PARAM, tab);
        return UiLinks.view(profileId, OPERATIONS_VIEW, query);
    }

    /**
     * One trace's span waterfall. Addressed through the attribute-search page because that view opens
     * the waterfall from the id alone - the operations page resolves a trace against the rows it has
     * loaded, which a bare id cannot assume.
     */
    public static String trace(String profileId, String traceId) {
        Map<String, String> query = UiLinks.query();
        query.put(TRACE_PARAM, traceId);
        return UiLinks.view(profileId, ATTRIBUTE_SEARCH_VIEW, query);
    }
}
