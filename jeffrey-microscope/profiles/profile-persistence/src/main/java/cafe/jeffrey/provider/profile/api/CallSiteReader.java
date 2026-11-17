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
 * the database then calculates complete totals and groups, hands back only the selected ranked sites,
 * and aggregates the events of each operation on each stack. High-cardinality grouping values never
 * become an unbounded collection of site rows in Java.
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

    /** The complete read's figures, once before any group or site, even for an empty read. */
    void onTotals(CallSiteTotals totals);

    /** Complete figures of a returned group, once before any of its sites. */
    void onGroup(CallSiteGroupRow group);

    /** One selected attributed path's events of one grouping value. */
    void onSite(CallSiteRow row);

    /** One operation's events on one stack, for every stack that belongs to a trace. */
    void onOperationStack(OperationStackRow row);

    T build();
}
