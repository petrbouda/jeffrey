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
import cafe.jeffrey.hub.model.repository.RecordingStatus;
import cafe.jeffrey.hub.model.repository.RepositoryFile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceSessionUnitsTest {

    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final long MB = 1024L * 1024L;

    @Test
    void emptyInputProducesNoUnits() {
        InstanceSessionUnits units = InstanceSessionUnits.of(List.of());

        assertTrue(units.units().isEmpty());
        assertTrue(units.sessionsNewestFirst().isEmpty());
    }

    @Test
    void consecutiveFailedSessionsCollapseIntoOneUnit() {
        InstanceSessionUnits units = InstanceSessionUnits.of(List.of(
                real("real-1", 1),
                failed("failed-1", 2),
                failed("failed-2", 3),
                real("real-2", 4)));

        assertEquals(List.of(
                        List.of("real-1"),
                        List.of("failed-1", "failed-2"),
                        List.of("real-2")),
                unitIds(units));
    }

    @Test
    void allFailedSessionsFormASingleUnit() {
        InstanceSessionUnits units = InstanceSessionUnits.of(List.of(
                failed("failed-1", 1),
                failed("failed-2", 2),
                failed("failed-3", 3)));

        assertEquals(List.of(List.of("failed-1", "failed-2", "failed-3")), unitIds(units));
    }

    @Test
    void realSessionSplitsFailedRuns() {
        InstanceSessionUnits units = InstanceSessionUnits.of(List.of(
                failed("failed-1", 1),
                real("real-1", 2),
                failed("failed-2", 3)));

        assertEquals(List.of(
                        List.of("failed-1"),
                        List.of("real-1"),
                        List.of("failed-2")),
                unitIds(units));
    }

    @Test
    void activeSessionSplitsFailedRuns() {
        // An active session has no finishedAt, so it never counts as failed-empty
        InstanceSessionUnits units = InstanceSessionUnits.of(List.of(
                failed("failed-1", 1),
                active("live", 2),
                failed("failed-2", 3)));

        assertEquals(List.of(
                        List.of("failed-1"),
                        List.of("live"),
                        List.of("failed-2")),
                unitIds(units));
    }

    @Test
    void unsortedInputIsSortedNewestFirst() {
        InstanceSessionUnits units = InstanceSessionUnits.of(List.of(
                failed("failed-2", 3),
                real("real-1", 2),
                failed("failed-1", 1)));

        assertEquals(List.of("failed-1", "real-1", "failed-2"), sessionIds(units.sessionsNewestFirst()));
    }

    private static List<List<String>> unitIds(InstanceSessionUnits units) {
        return units.units().stream()
                .map(InstanceSessionUnitsTest::sessionIds)
                .toList();
    }

    private static List<String> sessionIds(List<RecordingSession> sessions) {
        return sessions.stream()
                .map(RecordingSession::id)
                .toList();
    }

    private static RecordingSession real(String id, int hoursOld) {
        Instant createdAt = NOW.minus(Duration.ofHours(hoursOld));
        RepositoryFile file = new RepositoryFile(
                id + "-file", id + "-file", createdAt, 10 * MB,
                true, null);
        return session(id, createdAt, createdAt.plusSeconds(60), file);
    }

    private static RecordingSession failed(String id, int hoursOld) {
        Instant createdAt = NOW.minus(Duration.ofHours(hoursOld));
        return session(id, createdAt, createdAt.plusSeconds(5));
    }

    private static RecordingSession active(String id, int hoursOld) {
        return session(id, NOW.minus(Duration.ofHours(hoursOld)), null);
    }

    private static RecordingSession session(
            String id, Instant createdAt, Instant finishedAt, RepositoryFile... files) {

        RecordingStatus status = finishedAt != null ? RecordingStatus.FINISHED : RecordingStatus.ACTIVE;
        return new RecordingSession(
                id, id, "inst-1", createdAt, finishedAt, status, List.of(files), false);
    }
}
