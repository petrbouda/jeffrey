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
    }

    /**
     * Two sessions started in the same millisecond are ordered by id, as the volume's layout
     * orders them, so the one it calls newest comes first and a cap of one keeps it.
     */
    @Test
    void sessionsStartedTogetherAreOrderedByIdLikeTheLayout() {
        InstanceSessionUnits units = InstanceSessionUnits.of(List.of(
                newest("session-b", 1),
                real("session-a", 1)));

        assertEquals(List.of(List.of("session-b"), List.of("session-a")), unitIds(units));
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
    void theNewestSessionIsNeverFailed() {
        // No newer session has ended it, so having written nothing yet is no failure
        InstanceSessionUnits units = InstanceSessionUnits.of(List.of(
                newest("live", 1),
                failed("failed-1", 2),
                failed("failed-2", 3)));

        assertEquals(List.of(
                        List.of("live"),
                        List.of("failed-1", "failed-2")),
                unitIds(units));
    }

    @Test
    void unsortedInputIsSortedNewestFirst() {
        InstanceSessionUnits units = InstanceSessionUnits.of(List.of(
                failed("failed-2", 3),
                real("real-1", 2),
                failed("failed-1", 1)));

        assertEquals(List.of(List.of("failed-1"), List.of("real-1"), List.of("failed-2")), unitIds(units));
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
        return session(id, createdAt, false, file);
    }

    private static RecordingSession failed(String id, int hoursOld) {
        Instant createdAt = NOW.minus(Duration.ofHours(hoursOld));
        return session(id, createdAt, false);
    }

    private static RecordingSession newest(String id, int hoursOld) {
        return session(id, NOW.minus(Duration.ofHours(hoursOld)), true);
    }

    private static RecordingSession session(
            String id, Instant createdAt, boolean newest, RepositoryFile... files) {

        return new RecordingSession(id, id, "inst-1", createdAt, newest, List.of(files));
    }
}
