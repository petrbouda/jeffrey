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

import java.time.Instant;
import java.util.List;

/**
 * An immutable snapshot of a batch advisor run, polled by the UI. It carries the overall counters the
 * timeline header needs ({@code done / total · pct}) plus one {@link AdvisorProgress} per event type, so
 * the page can render each type's pipeline without a second request.
 *
 * <p>Like {@link AdvisorProgress} it holds no result payload: a completed type's artifacts live in the
 * profile database and are read from there, so a reload after the batch finished shows the same thing as
 * a reload during it.</p>
 *
 * @param profileId   the profile the batch belongs to
 * @param status      the overall state, or null when no batch has ever run for this profile
 * @param done        the number of types that have settled (completed or failed)
 * @param total       the number of types in the batch
 * @param pct         {@code done / total} as a whole percentage
 * @param startedAt   when the earliest type started, or null when idle
 * @param completedAt when the last type settled, or null while the batch is still running
 * @param types       the per-type progress snapshots, in the batch's declared order
 */
public record BatchAdvisorProgress(
        String profileId,
        BatchStatus status,
        int done,
        int total,
        int pct,
        Instant startedAt,
        Instant completedAt,
        List<AdvisorProgress> types) {

    public BatchAdvisorProgress {
        types = types == null ? List.of() : List.copyOf(types);
    }

    /**
     * What the endpoint answers for a profile whose Advisor has never been launched. A null status lets
     * the UI show the initial page (source folder + Run) rather than a timeline for a run that never
     * happened.
     */
    public static BatchAdvisorProgress idle(String profileId) {
        return new BatchAdvisorProgress(profileId, null, 0, 0, 0, null, null, List.of());
    }

    public boolean isRunning() {
        return status == BatchStatus.QUEUED || status == BatchStatus.RUNNING;
    }
}
