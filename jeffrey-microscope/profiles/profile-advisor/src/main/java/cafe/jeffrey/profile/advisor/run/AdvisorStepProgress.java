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

/**
 * The live progress of one timed step (Prompt / Recommendation / Patch) within an event type's run,
 * mirroring the heap-dump init stage progress so the shared timeline renders both the same way.
 *
 * <p>{@code durationMs} is set once the step has finished; {@code elapsedMs} is the running time of the
 * step currently in progress, computed from the injected clock so a reconnecting UI resumes the timer
 * without clock skew. Only one of the two is meaningful at a time.</p>
 *
 * @param step       the pipeline step name (an {@link AdvisorStatus} name, e.g. {@code RECOMMENDING})
 * @param status     one of {@link #PENDING}, {@link #IN_PROGRESS}, {@link #COMPLETED}, {@link #FAILED}
 * @param durationMs the measured duration once completed, or null
 * @param elapsedMs  the live elapsed time while in progress, or null
 */
public record AdvisorStepProgress(
        String step,
        String status,
        Long durationMs,
        Long elapsedMs) {

    public static final String PENDING = "pending";
    public static final String IN_PROGRESS = "in_progress";
    public static final String COMPLETED = "completed";
    public static final String FAILED = "failed";
}
