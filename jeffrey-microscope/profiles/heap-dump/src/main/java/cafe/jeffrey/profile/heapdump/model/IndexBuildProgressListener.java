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

package cafe.jeffrey.profile.heapdump.model;

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
