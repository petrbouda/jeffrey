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

package cafe.jeffrey.hub.core.scheduler.job;

import cafe.jeffrey.hub.model.repository.RecordingSession;

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
