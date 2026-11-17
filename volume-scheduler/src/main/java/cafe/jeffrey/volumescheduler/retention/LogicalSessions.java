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

package cafe.jeffrey.volumescheduler.retention;

import cafe.jeffrey.volumescheduler.volume.RecordingSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The logical sessions of one instance, which the per-instance cap counts: a consecutive run of
 * failed-empty sessions (a crash loop) collapses into one, while every session that produced
 * data — and the instance's newest, which is never failed — is one of its own and splits failed
 * runs, so a crash loop counts as one session toward the cap.
 *
 * <p>Logical sessions and the sessions inside them are ordered newest first by {@code createdAt},
 * ties broken by id the way the volume's layout orders sessions ({@code ProjectTree}), so the
 * session the layout calls newest is always the first and never the one a cap cuts.</p>
 */
final class LogicalSessions {

    private static final Comparator<RecordingSession> NEWEST_FIRST = Comparator
            .comparing(RecordingSession::createdAt)
            .thenComparing(RecordingSession::id)
            .reversed();

    private LogicalSessions() {
    }

    /** One instance's sessions grouped into logical sessions, newest first. */
    static List<List<RecordingSession>> of(List<RecordingSession> sessions) {
        List<List<RecordingSession>> logical = new ArrayList<>();
        List<RecordingSession> failedRun = new ArrayList<>();

        for (RecordingSession session : sessions.stream().sorted(NEWEST_FIRST).toList()) {
            if (session.isFailedEmpty()) {
                failedRun.add(session);
            } else {
                flush(logical, failedRun);
                logical.add(List.of(session));
            }
        }
        flush(logical, failedRun);
        return List.copyOf(logical);
    }

    private static void flush(List<List<RecordingSession>> logical, List<RecordingSession> failedRun) {
        if (failedRun.isEmpty()) {
            return;
        }
        logical.add(List.copyOf(failedRun));
        failedRun.clear();
    }
}
