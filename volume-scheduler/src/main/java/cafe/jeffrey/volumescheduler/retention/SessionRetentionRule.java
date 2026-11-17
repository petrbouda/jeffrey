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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Whole sessions, per instance, by two independent rules: whose newest recording started longer
 * ago than {@code retention}, or beyond the newest {@code maxSessions} logical sessions, where a
 * consecutive run of failed-empty sessions counts as one ({@link LogicalSessions}).
 *
 * <p>Age is measured from the newest recording, not the session's start, so a running JVM's
 * session — its profiler loops, so its newest chunk is recent — is never aged out, while an
 * instance that stopped goes with its last session once the window has passed. The cap never takes
 * an instance's newest session: it is the first logical session. The two stay one rule: deleting a
 * session between two failed runs first would merge them into one before the cap counted them.</p>
 */
record SessionRetentionRule(Duration retention, int maxSessions) implements RetentionRule {

    @Override
    public void apply(RetentionPass pass) {
        Map<String, List<RecordingSession>> sessionsByInstance = pass.sessions().stream()
                .collect(Collectors.groupingBy(RecordingSession::instanceId));

        List<RecordingSession> expired = new ArrayList<>();
        for (List<RecordingSession> sessions : sessionsByInstance.values()) {
            expired.addAll(expiredOf(LogicalSessions.of(sessions), pass.now()));
        }

        expired.forEach(pass::deleteSession);
    }

    private List<RecordingSession> expiredOf(List<List<RecordingSession>> logicalSessions, Instant now) {
        Instant cutoff = now.minus(retention);
        List<RecordingSession> expired = new ArrayList<>();
        for (int index = 0; index < logicalSessions.size(); index++) {
            boolean overCap = index >= maxSessions;
            for (RecordingSession session : logicalSessions.get(index)) {
                boolean aged = session.lastRecordingStart().isBefore(cutoff);
                if (aged || overCap) {
                    expired.add(session);
                }
            }
        }
        return expired;
    }
}
