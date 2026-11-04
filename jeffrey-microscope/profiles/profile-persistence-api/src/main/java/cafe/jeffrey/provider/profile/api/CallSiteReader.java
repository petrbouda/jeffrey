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


package cafe.jeffrey.provider.profile.api;

import cafe.jeffrey.jfrparser.api.type.JfrStackTrace;

/**
 * What a call-site read is built by, in the order the repository calls it: each distinct stack the events
 * were committed on is first charged to a site path the reader decides — its own frames, read in Java — and
 * the database then aggregates the events of each path and grouping value, per trace included, and the
 * events of each operation on each stack. The rows handed back are bounded by the distinct stacks and
 * sites, never by the number of events or traces.
 *
 * @param <T> what the reader builds
 */
public interface CallSiteReader<T> {

    /** Events charged to no site: no recorded stack, or no frame of the reader's own code on it. */
    int UNATTRIBUTED = -1;

    /**
     * The site path a stack's events are charged to: an index the reader assigns, the same for stacks that
     * share a path, or {@link #UNATTRIBUTED}. Called once per distinct stack, before any row.
     */
    int pathOf(JfrStackTrace stackTrace);

    /** One path's events of one grouping value, the unattributed path's included. */
    void onSite(CallSiteRow row);

    /** One operation's events on one stack, for every stack that belongs to a trace. */
    void onOperationStack(OperationStackRow row);

    T build();
}
