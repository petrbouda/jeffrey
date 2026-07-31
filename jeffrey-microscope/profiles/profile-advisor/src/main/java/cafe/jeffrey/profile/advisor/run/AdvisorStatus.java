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
 * run the AI analysis, check what came back, then finish in a terminal {@link #COMPLETED} or
 * {@link #FAILED} state.
 *
 * <p>{@link #ORDER} is the sequence the UI renders as a stage timeline, so a user watching a long run
 * can see which step it is on rather than a spinner that says nothing.</p>
 */
public enum AdvisorStatus {

    /** Waiting for a generation slot; another run is using them all. */
    QUEUED("Waiting for a generation slot…"),

    /** Building the flamegraph prompt, or loading the cached one. */
    PREPARING_PROMPT("Preparing the profile summary…"),

    /** Validating the configured source folder and reading its commit. */
    RESOLVING_SOURCE("Locating the source folder…"),

    ANALYZING("Analyzing the source…"),

    /** Grounding the model's claims and checking the patch against the source tree. */
    VERIFYING("Checking findings against the profile and the patch…"),

    COMPLETED("Recommendations ready"),
    FAILED("Advisor run failed");

    /**
     * The non-terminal stages in the order they happen. Terminal states are excluded: they are an
     * outcome, not a step, and rendering them as one would imply a run can be "in" FAILED for a while.
     */
    public static final List<AdvisorStatus> ORDER =
            List.of(QUEUED, PREPARING_PROMPT, RESOLVING_SOURCE, ANALYZING, VERIFYING);

    private final String message;

    AdvisorStatus(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
