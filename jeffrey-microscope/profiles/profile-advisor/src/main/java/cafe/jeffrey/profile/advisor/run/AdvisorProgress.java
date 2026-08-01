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

import java.time.Instant;

/**
 * An immutable snapshot of one event type's advisor run, polled by the UI.
 *
 * <p>The snapshot deliberately carries no result payload. A completed run has already been stored in
 * the profile database, and the page reads it from there — so progress answers "where is it up to"
 * and nothing else, and a reload after the run finished shows the same thing as a reload during it.</p>
 *
 * <p>{@code steps} carries the four timed pipeline steps (Prompt / Source / Analyze / Ground) so the
 * timeline can render each one's tick and measured time. {@code errorMessage} is populated only on
 * {@link AdvisorStatus#FAILED}.</p>
 */
public record AdvisorProgress(
        String profileId,
        String eventType,
        AdvisorStatus status,
        String message,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        List<AdvisorStepProgress> steps) {

    private static final String IDLE_MESSAGE = "No advisor run has been started for this profile";

    public AdvisorProgress {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /**
     * What the endpoint answers for a profile that has never run. Distinguishable from a finished run
     * because there is no {@code completedAt}, so the UI can show a start button rather than a result
     * banner for a run that never happened.
     */
    public static AdvisorProgress idle(String profileId) {
        return new AdvisorProgress(profileId, null, null, IDLE_MESSAGE, null, null, null, List.of());
    }

    public boolean isRunning() {
        return status != null && !status.isTerminal();
    }
}
