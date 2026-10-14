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
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.hub.model.repository.RecordingSession;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Whole finished sessions, per instance, by two independent rules: older than {@code retention}
 * (measured from the session's start), or beyond the newest {@code maxSessions} logical sessions,
 * where a consecutive run of failed-empty sessions counts as one ({@link InstanceSessionUnits}).
 *
 * <p>Retained sessions never expire and occupy no slot. The live session occupies a slot but is
 * never deleted. An instance that is not FINISHED keeps its newest finished session that produced
 * data, whatever its age or position.</p>
 */
record SessionRetentionRule(Duration retention, int maxSessions) implements RetentionRule {

    SessionRetentionRule {
        if (maxSessions <= 0) {
            throw new IllegalArgumentException("max-sessions must be positive: " + maxSessions);
        }
    }

    @Override
    public void apply(ProjectRetentionPass pass) {
        Map<String, ProjectInstanceInfo> instancesById = pass.instances().stream()
                .collect(Collectors.toMap(ProjectInstanceInfo::id, Function.identity()));

        Map<String, List<RecordingSession>> sessionsByInstance = pass.sessions().stream()
                .filter(session -> !session.retained())
                .collect(Collectors.groupingBy(RecordingSession::instanceId));

        List<RecordingSession> expired = new ArrayList<>();
        for (var entry : sessionsByInstance.entrySet()) {
            ProjectInstanceInfo instance = instancesById.get(entry.getKey());
            boolean instanceFinished = instance != null && instance.status() == ProjectInstanceStatus.FINISHED;
            expired.addAll(expiredOf(InstanceSessionUnits.of(entry.getValue()), instanceFinished, pass.now()));
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

    private List<RecordingSession> expiredOf(InstanceSessionUnits units, boolean instanceFinished, Instant now) {
        // Neither a failed-empty session nor the live one may take the keep-newest slot: the live
        // one is safe anyway, and either would leave the newest real session unprotected
        String protectedSessionId = units.sessionsNewestFirst().stream()
                .filter(session -> session.finishedAt() != null)
                .filter(session -> !session.isFailedEmpty())
                .map(RecordingSession::id)
                .findFirst()
                .orElse(null);

        List<RecordingSession> expired = new ArrayList<>();
        List<List<RecordingSession>> unitList = units.units();
        for (int unitIndex = 0; unitIndex < unitList.size(); unitIndex++) {
            boolean overCap = unitIndex >= maxSessions;
            for (RecordingSession session : unitList.get(unitIndex)) {
                if (session.finishedAt() == null) {
                    continue;
                }
                if (!instanceFinished && session.id().equals(protectedSessionId)) {
                    continue;
                }
                boolean ageExpired = now.isAfter(session.createdAt().plus(retention));
                if (ageExpired || overCap) {
                    expired.add(session);
                }
            }
        }
        return expired;
    }
}
