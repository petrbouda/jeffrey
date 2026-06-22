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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.microscope.persistence.api.HubAddress;
import cafe.jeffrey.microscope.persistence.api.HubInfo;
import cafe.jeffrey.microscope.persistence.api.RecordingGroup;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.shared.common.encryption.MachineFingerprint;
import cafe.jeffrey.shared.common.encryption.SecretEncryptor;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.RecordingFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.SQLiteTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the SQLite store end-to-end: the V001 Flyway schema (flyway-core's bundled SQLite support)
 * is applied to a fresh in-memory SQLite database per test via {@link SQLiteTest}, and the JDBC
 * repositories round-trip against it with foreign-key enforcement (so {@code ON DELETE CASCADE} fires).
 * The production WAL/pragma wiring is covered separately by {@code DataSourceConfigurationTest}.
 */
@SQLiteTest(migration = "classpath:db/migration/performance-analyst/core")
class SqlitePersistenceTest {

    private static final Instant T = Instant.ofEpochMilli(1_000L);
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(42_000L), ZoneOffset.UTC);

    private DatabaseClientProvider clientProvider;

    @BeforeEach
    void setUp(DataSource dataSource) {
        clientProvider = new DatabaseClientProvider(dataSource);
    }

    @Nested
    class GeneratedPrompts {

        @Test
        void upsertReadAndOverwrite() {
            JdbcGeneratedPromptRepository repo = new JdbcGeneratedPromptRepository(clientProvider);
            assertTrue(repo.findByRecording("rec-1").isEmpty());

            repo.upsert(new GeneratedPrompt("rec-1", "jdk.ExecutionSample", "CPU", 39668, "# cpu", T));
            repo.upsert(new GeneratedPrompt("rec-1", "profiler.WallClockSample", "Wall-Clock", 4409322, "# wall", T));

            List<GeneratedPrompt> prompts = repo.findByRecording("rec-1");
            assertEquals(2, prompts.size());

            // upsert on the (recording, event type) key overwrites in place
            repo.upsert(new GeneratedPrompt("rec-1", "jdk.ExecutionSample", "CPU", 100, "# updated", T));
            List<GeneratedPrompt> updated = repo.findByRecording("rec-1");
            assertEquals(2, updated.size());
            GeneratedPrompt cpu = updated.stream()
                    .filter(p -> p.eventType().equals("jdk.ExecutionSample")).findFirst().orElseThrow();
            assertEquals(100, cpu.samples());
            assertEquals("# updated", cpu.markdown());
        }
    }

    @Nested
    class Projects {

        @Test
        void insertFindDelete() {
            JdbcProjectRepository repo = new JdbcProjectRepository(clientProvider);
            repo.insert(new Project("p1", "Project One", "desc", T, T));

            assertEquals("Project One", repo.findById("p1").orElseThrow().name());
            assertEquals(1, repo.findAll().size());

            repo.delete("p1");
            assertTrue(repo.findById("p1").isEmpty());
        }

        @Test
        void aiConfigurationUpsertAndOverwrite() {
            new JdbcProjectRepository(clientProvider).insert(new Project("p2", "Project Two", null, T, T));
            JdbcProjectAiConfigurationRepository repo = new JdbcProjectAiConfigurationRepository(clientProvider);
            assertFalse(repo.find("p2").isPresent());

            repo.upsert(new ProjectAiConfiguration("p2", "claude", "claude-opus-4-8", 1.0, T));
            ProjectAiConfiguration config = repo.find("p2").orElseThrow();
            assertEquals("claude", config.provider());
            assertEquals(1.0, config.pruneThresholdPct());

            repo.upsert(new ProjectAiConfiguration("p2", "openai", "gpt", 2.5, T));
            assertEquals("openai", repo.find("p2").orElseThrow().provider());
            assertEquals(2.5, repo.find("p2").orElseThrow().pruneThresholdPct());
        }

        @Test
        void deletingProjectCascadesAiConfiguration() {
            new JdbcProjectRepository(clientProvider).insert(new Project("p3", "Project Three", null, T, T));
            JdbcProjectAiConfigurationRepository aiRepo = new JdbcProjectAiConfigurationRepository(clientProvider);
            aiRepo.upsert(new ProjectAiConfiguration("p3", "claude", "claude-opus-4-8", 1.0, T));
            assertTrue(aiRepo.find("p3").isPresent());

            // ON DELETE CASCADE only fires when foreign_keys is enforced — proves the pragma is on.
            new JdbcProjectRepository(clientProvider).delete("p3");
            assertFalse(aiRepo.find("p3").isPresent());
        }
    }

    @Nested
    class VersionControlSystems {

        private static final String CREDENTIALS_JSON = "{\"token\":\"secret-token\"}";

        private JdbcVersionControlSystemStore store() {
            return new JdbcVersionControlSystemStore(clientProvider, new SecretEncryptor(new MachineFingerprint()));
        }

        @Test
        void upsertReadsBackDecryptedAndOverwrites() throws Exception {
            // project_id is a soft reference to a remote workspace project — no local projects row needed.
            JdbcVersionControlSystemStore store = store();
            assertTrue(store.findByProject("vp1").isEmpty());

            store.upsert(new VersionControlSystem(
                    "vs1", "vp1", Platform.GITHUB, "https://github.com/petrbouda/jeffrey.git", CREDENTIALS_JSON, T, T));

            VersionControlSystem loaded = store.findByProject("vp1").orElseThrow();
            assertEquals(Platform.GITHUB, loaded.platform());
            assertEquals("https://github.com/petrbouda/jeffrey.git", loaded.url());
            assertEquals(CREDENTIALS_JSON, loaded.credentials());
            assertTrue(loaded.hasCredentials());

            // credentials are encrypted at rest: the raw column never contains the plaintext token
            assertFalse(readRawCredentials("vp1").contains("secret-token"));

            // upsert on the project key overwrites in place; a null token clears the stored credentials
            store.upsert(new VersionControlSystem(
                    "vs1", "vp1", Platform.GITLAB, "https://gitlab.com/group/repo.git", null, T, T));
            VersionControlSystem updated = store.findByProject("vp1").orElseThrow();
            assertEquals(Platform.GITLAB, updated.platform());
            assertEquals("https://gitlab.com/group/repo.git", updated.url());
            assertFalse(updated.hasCredentials());
        }

        @Test
        void deleteRemovesTheRow() {
            JdbcVersionControlSystemStore store = store();
            store.upsert(new VersionControlSystem(
                    "vs2", "vp2", Platform.GITHUB, "https://example.com/repo.git", null, T, T));
            assertTrue(store.findByProject("vp2").isPresent());

            store.delete("vp2");
            assertFalse(store.findByProject("vp2").isPresent());
        }

        private String readRawCredentials(String projectId) throws Exception {
            try (Connection connection = clientProvider.dataSource().getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "SELECT credentials FROM version_control_systems WHERE project_id = '" + projectId + "'")) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    @Nested
    class Hubs {

        @Test
        void createFindAndDelete() {
            JdbcHubsRepository repo = new JdbcHubsRepository(clientProvider);
            assertTrue(repo.findAll().isEmpty());

            repo.create(new HubInfo("hub-1", "Prod", new HubAddress("prod.example.com", 443, false), T));
            repo.create(new HubInfo("hub-2", "Lan", new HubAddress("10.0.0.5", 8080, true), T));

            assertEquals(2, repo.findAll().size());

            HubInfo found = repo.find("hub-2").orElseThrow();
            assertEquals("Lan", found.name());
            assertEquals("10.0.0.5", found.address().hostname());
            assertEquals(8080, found.address().port());
            assertTrue(found.address().plaintext());
            assertEquals(T, found.createdAt());

            repo.delete("hub-1");
            assertEquals(1, repo.findAll().size());
            assertTrue(repo.find("hub-1").isEmpty());
        }
    }

    @Nested
    class Recordings {

        private JdbcRecordingRepository repo() {
            return new JdbcRecordingRepository(clientProvider, null, CLOCK);
        }

        private Recording recording(String id, String groupId) {
            return new Recording(
                    id, id + "-name", null, groupId, RecordingEventSource.JDK,
                    T, T, T, false, null, null, List.of());
        }

        private RecordingFile file(String id, String recordingId) {
            return new RecordingFile(id, recordingId, id + ".jfr", SupportedRecordingFile.JFR, T, 123L);
        }

        @Test
        void insertWithFileFindAllAndDelete() {
            JdbcRecordingRepository repo = repo();
            repo.insertRecording(recording("r1", null), file("f1", "r1"));

            List<Recording> all = repo.findAllRecordings();
            assertEquals(1, all.size());
            Recording loaded = all.getFirst();
            assertEquals("r1-name", loaded.recordingName());
            assertEquals(RecordingEventSource.JDK, loaded.eventSource());
            assertEquals(T, loaded.createdAt());
            assertFalse(loaded.hasProfile());
            assertEquals(1, loaded.files().size());
            assertEquals("f1.jfr", loaded.files().getFirst().filename());

            Recording byId = repo.findRecording("r1").orElseThrow();
            assertEquals(1, byId.files().size());

            repo.deleteRecordingWithFiles("r1");
            assertTrue(repo.findAllRecordings().isEmpty());
            assertTrue(repo.findRecording("r1").isEmpty());
        }

        @Test
        void nullStartedAndFinishedRoundTripAsNull() {
            JdbcRecordingRepository repo = repo();
            Recording rec = new Recording(
                    "r2", "r2-name", null, null, RecordingEventSource.JDK,
                    T, null, null, false, null, null, List.of());
            repo.insertRecording(rec, file("f2", "r2"));

            Recording loaded = repo.findRecording("r2").orElseThrow();
            assertEquals(T, loaded.createdAt());
            assertEquals(null, loaded.recordingStartedAt());
            assertEquals(null, loaded.recordingFinishedAt());
        }

        @Test
        void groupsCreateMoveListAndDeleteCascadesRecordings() {
            JdbcRecordingRepository repo = repo();

            String groupId = repo.insertGroup("My Group");
            assertTrue(repo.groupExists(groupId));
            RecordingGroup group = repo.findGroupById(groupId).orElseThrow();
            assertEquals("My Group", group.name());
            assertEquals(CLOCK.instant(), group.createdAt());
            assertEquals(1, repo.findAllRecordingGroups().size());

            repo.insertRecording(recording("r3", null), file("f3", "r3"));
            repo.updateRecordingGroup("r3", groupId);
            assertEquals(1, repo.findRecordingsByGroupId(groupId).size());

            // deleteGroup removes the group and the recordings (plus files) it contained
            repo.deleteGroup(groupId);
            assertFalse(repo.groupExists(groupId));
            assertTrue(repo.findRecording("r3").isEmpty());
        }
    }

    @Nested
    class RecordingTags {

        @Test
        void insertListForOneAndManyThenDelete() {
            JdbcRecordingTagsRepository repo = new JdbcRecordingTagsRepository(clientProvider);
            repo.insert("r1", Map.of("env", "prod", "team", "core"));
            repo.insert("r2", Map.of("env", "qa"));

            List<RecordingTag> r1Tags = repo.listForRecording("r1");
            assertEquals(2, r1Tags.size());

            Map<String, List<RecordingTag>> many = repo.listForRecordings(List.of("r1", "r2"));
            assertEquals(2, many.size());
            assertEquals(1, many.get("r2").size());
            assertEquals("qa", many.get("r2").getFirst().value());

            repo.deleteForRecording("r1");
            assertTrue(repo.listForRecording("r1").isEmpty());
            assertEquals(1, repo.listForRecordings(List.of("r1", "r2")).size());
        }
    }
}
