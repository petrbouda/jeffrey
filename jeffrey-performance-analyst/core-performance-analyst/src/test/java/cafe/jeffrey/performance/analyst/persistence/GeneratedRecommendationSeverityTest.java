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

package cafe.jeffrey.performance.analyst.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.RecordingFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import cafe.jeffrey.shared.common.model.Severity;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.SQLiteTest;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the severity column round-trips and that the Overview ranking/counting queries pick each
 * recording's worst severity, order by severity then recency, and join the recording name.
 */
@SQLiteTest(migration = "classpath:db/migration/performance-analyst/core")
class GeneratedRecommendationSeverityTest {

    private static final Instant T = Instant.ofEpochMilli(1_000L);
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(42_000L), ZoneOffset.UTC);

    private DatabaseClientProvider clientProvider;
    private JdbcGeneratedRecommendationRepository repo;
    private JdbcRecordingRepository recordingRepo;

    @BeforeEach
    void setUp(DataSource dataSource) {
        clientProvider = new DatabaseClientProvider(dataSource);
        repo = new JdbcGeneratedRecommendationRepository(clientProvider);
        recordingRepo = new JdbcRecordingRepository(clientProvider, null, CLOCK);
    }

    private void insertRecording(String id) {
        Recording recording = new Recording(
                id, id + "-name", null, null, RecordingEventSource.JDK, T, T, T, false, null, null, List.of());
        RecordingFile file = new RecordingFile(id + "-f", id, id + ".jfr", SupportedRecordingFile.JFR, T, 123L);
        recordingRepo.insertRecording(recording, file);
    }

    private GeneratedRecommendation rec(String recordingId, String eventType, Severity severity, long generatedAt) {
        return new GeneratedRecommendation(
                recordingId, eventType, "hub-1", "ws-1", "proj-" + recordingId, recordingId + " Project",
                severity, "## Summary\nHot path in " + recordingId, null, Instant.ofEpochMilli(generatedAt));
    }

    @Test
    void severityRoundTrips() {
        insertRecording("r1");
        repo.upsert(rec("r1", "jdk.ExecutionSample", Severity.HIGH, 1_000L));

        GeneratedRecommendation stored = repo.findByRecording("r1").getFirst();
        assertEquals(Severity.HIGH, stored.severity());
        assertEquals("r1 Project", stored.projectName());
        assertEquals("hub-1", stored.hubId());
    }

    @Test
    void topBySeverityPicksWorstPerRecordingAndOrders() {
        insertRecording("r1");
        insertRecording("r2");
        insertRecording("r3");
        // r1's worst is CRITICAL (across two event types); r2 HIGH; r3 LOW
        repo.upsert(rec("r1", "jdk.ExecutionSample", Severity.MEDIUM, 1_000L));
        repo.upsert(rec("r1", "profiler.WallClockSample", Severity.CRITICAL, 2_000L));
        repo.upsert(rec("r2", "jdk.ExecutionSample", Severity.HIGH, 3_000L));
        repo.upsert(rec("r3", "jdk.ExecutionSample", Severity.LOW, 4_000L));

        List<TopSeverityRecommendation> top = repo.findTopBySeverity(10);

        assertEquals(List.of("r1", "r2", "r3"), top.stream().map(TopSeverityRecommendation::recordingId).toList());
        assertEquals(Severity.CRITICAL, top.getFirst().severity());
        assertEquals("r1-name", top.getFirst().recordingName());
    }

    @Test
    void countsAreOnePerRecordingByWorstSeverity() {
        insertRecording("r1");
        insertRecording("r2");
        repo.upsert(rec("r1", "jdk.ExecutionSample", Severity.LOW, 1_000L));
        repo.upsert(rec("r1", "profiler.WallClockSample", Severity.CRITICAL, 2_000L));
        repo.upsert(rec("r2", "jdk.ExecutionSample", Severity.HIGH, 3_000L));

        Map<Severity, Integer> counts = repo.countBySeverity().stream()
                .collect(Collectors.toMap(SeverityCount::severity, SeverityCount::count));

        assertEquals(1, counts.getOrDefault(Severity.CRITICAL, 0));
        assertEquals(1, counts.getOrDefault(Severity.HIGH, 0));
        assertEquals(0, counts.getOrDefault(Severity.LOW, 0), "r1's LOW row must not be counted; its worst is CRITICAL");
    }
}
