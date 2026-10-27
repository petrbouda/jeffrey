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

import cafe.jeffrey.hub.core.jfr.JfrNotificationEmitter;
import cafe.jeffrey.hub.model.repository.RecordingSession;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Whole sessions, per instance, by two independent rules: whose last recorded data is older than
 * {@code retention}, or beyond the newest {@code maxSessions} logical sessions, where a consecutive
 * run of failed-empty sessions counts as one ({@link InstanceSessionUnits}).
 *
 * <p>Age is measured from the last data, not the start, so a session still recording — whose
 * newest recording is always recent — is never aged out, while an instance that stopped goes with
 * its last session once the window has passed. The cap never takes an instance's newest session:
 * it is the first unit.</p>
 */
record SessionRetentionRule(Duration retention, int maxSessions) implements RetentionRule {

    SessionRetentionRule {
        if (maxSessions <= 0) {
            throw new IllegalArgumentException("max-sessions must be positive: " + maxSessions);
        }
    }

    @Override
    public void apply(ProjectRetentionPass pass) {
        Map<String, List<RecordingSession>> sessionsByInstance = pass.sessions().stream()
                .collect(Collectors.groupingBy(RecordingSession::instanceId));

        List<RecordingSession> expired = new ArrayList<>();
        for (List<RecordingSession> sessions : sessionsByInstance.values()) {
            expired.addAll(expiredOf(InstanceSessionUnits.of(sessions), pass.now()));
        }

        int deleted = 0;
        for (RecordingSession session : expired) {
            if (pass.deleteSession(session)) {
                deleted++;
            }
        }
        if (deleted > 0) {
            JfrNotificationEmitter.sessionsCleaned(pass.projectName(), deleted);
        }
    }

    private List<RecordingSession> expiredOf(InstanceSessionUnits units, Instant now) {
        Instant cutoff = now.minus(retention);
        List<RecordingSession> expired = new ArrayList<>();
        List<List<RecordingSession>> unitList = units.units();
        for (int unitIndex = 0; unitIndex < unitList.size(); unitIndex++) {
            boolean overCap = unitIndex >= maxSessions;
            for (RecordingSession session : unitList.get(unitIndex)) {
                boolean aged = session.lastDataAt().isBefore(cutoff);
                if (aged || overCap) {
                    expired.add(session);
                }
            }
        }
        return expired;
    }
}
