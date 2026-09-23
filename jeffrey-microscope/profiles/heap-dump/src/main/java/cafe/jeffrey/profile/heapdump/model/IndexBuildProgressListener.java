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

package cafe.jeffrey.profile.heapdump.model;

import cafe.jeffrey.profile.common.pipeline.SubPhaseTiming;

/**
 * Notified as each index-build sub-phase completes, so callers can surface
 * real-time progress instead of waiting for the whole atomic build to finish.
 *
 * <p>The build runs synchronously on a single thread (each parallel phase joins
 * before returning), so {@link #onSubPhase} is invoked sequentially, one
 * sub-phase at a time — no listener-side synchronization is required.
 */
@FunctionalInterface
public interface IndexBuildProgressListener {

    /** A no-op listener for callers that don't track build progress. */
    IndexBuildProgressListener NOOP = timing -> {
    };

    /**
     * Called immediately before a sub-phase begins executing, so a listener can
     * advance its stage view at the real phase boundary rather than lagging a
     * phase behind (which it would if it only reacted to completions).
     */
    default void onSubPhaseStarted(String subPhaseName) {
    }

    /** Called once a sub-phase has completed, carrying its measured timing. */
    void onSubPhase(SubPhaseTiming timing);
}
