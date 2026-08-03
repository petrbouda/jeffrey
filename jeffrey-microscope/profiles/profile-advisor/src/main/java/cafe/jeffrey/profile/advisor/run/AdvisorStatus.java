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

package cafe.jeffrey.profile.advisor.run;

import java.util.List;

/**
 * Lifecycle of an advisor run: wait for a slot, build or load the prompt, resolve the source folder,
 * let the model review the source, build the patch it proposed, then finish in a terminal
 * {@link #COMPLETED} or {@link #FAILED} state.
 *
 * <p>The names double as the pipeline's stage ids and the frontend's label keys, so renaming a
 * constant is a wire-format change.</p>
 */
public enum AdvisorStatus {

    /** Waiting for a generation slot; another run is using them all. */
    QUEUED,

    /** Building the flamegraph prompt, or loading the cached one. */
    PREPARING_PROMPT,

    /** Validating the configured source folder and reading its commit. */
    RESOLVING_SOURCE,

    /** The model reading the source through the read-only tools, and its answer being split apart. */
    REVIEWING,

    /** The proposed diff is being repaired into an applicable patch and checked against the checkout. */
    BUILDING_PATCH,

    COMPLETED,
    FAILED;

    /**
     * The timed pipeline steps, in the order they happen — everything except {@link #QUEUED}, which is
     * a wait for a slot rather than work, and the terminal states, which are an outcome rather than a
     * step. Each of these is measured and shown as a row under its event type in the run timeline.
     */
    public static final List<AdvisorStatus> STEPS =
            List.of(PREPARING_PROMPT, RESOLVING_SOURCE, REVIEWING, BUILDING_PATCH);

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
