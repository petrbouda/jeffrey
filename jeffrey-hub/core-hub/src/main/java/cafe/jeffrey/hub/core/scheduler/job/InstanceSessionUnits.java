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

package cafe.jeffrey.hub.core.scheduler.job;

import cafe.jeffrey.shared.common.model.repository.RecordingSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Logical retention units of one instance's sessions: a consecutive run of failed-empty
 * sessions (a crash loop) collapses into a single unit, while every session that produced
 * data — including the still-active one — forms a unit of its own and splits failed runs.
 * This mirrors the collapsed crash-loop groups the UI renders (sessionGrouping.ts).
 *
 * <p>Units and the sessions inside them are ordered newest-first by {@code createdAt}.
 * Failed-empty classification relies on {@link RecordingSession#isFailedEmpty()}, so the
 * sessions must have been loaded WITH files.</p>
 */
final class InstanceSessionUnits {

    private final List<List<RecordingSession>> units;

    private InstanceSessionUnits(List<List<RecordingSession>> units) {
        this.units = units;
    }

    static InstanceSessionUnits of(List<RecordingSession> sessions) {
        List<RecordingSession> sorted = sessions.stream()
                .sorted(Comparator.comparing(RecordingSession::createdAt).reversed())
                .toList();

        List<List<RecordingSession>> units = new ArrayList<>();
        List<RecordingSession> currentFailedRun = new ArrayList<>();

        for (RecordingSession session : sorted) {
            if (session.isFailedEmpty()) {
                currentFailedRun.add(session);
            } else {
                flushFailedRun(units, currentFailedRun);
                units.add(List.of(session));
            }
        }
        flushFailedRun(units, currentFailedRun);

        return new InstanceSessionUnits(List.copyOf(units));
    }

    private static void flushFailedRun(List<List<RecordingSession>> units, List<RecordingSession> failedRun) {
        if (failedRun.isEmpty()) {
            return;
        }
        units.add(List.copyOf(failedRun));
        failedRun.clear();
    }

    /** Logical units, newest-first; each unit's sessions are also newest-first. */
    List<List<RecordingSession>> units() {
        return units;
    }

    /** All sessions flattened, newest-first — the order the protection scan expects. */
    List<RecordingSession> sessionsNewestFirst() {
        return units.stream()
                .flatMap(List::stream)
                .toList();
    }
}
