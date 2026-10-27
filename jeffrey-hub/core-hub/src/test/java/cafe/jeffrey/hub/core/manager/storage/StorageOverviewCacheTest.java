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

package cafe.jeffrey.hub.core.manager.storage;

import cafe.jeffrey.hub.core.manager.storage.StorageOverview.ProjectStorage;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache.CachedOverview;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RepositoryFile;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StorageOverviewCacheTest {

    private static final Instant NOW = Instant.parse("2026-08-10T10:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant LAST_ACTIVITY = Instant.parse("2026-08-09T10:00:00Z");

    private static final WorkspaceInfo WORKSPACE =
            new WorkspaceInfo("ws-1", "production", CREATED_AT, 1);
    private static final ProjectInfo PROJECT =
            new ProjectInfo("prj-1", "order-service", "ws-1", CREATED_AT, Map.of());

    private StorageOverviewCache cache;

    @BeforeEach
    void setUp() {
        cache = new StorageOverviewCache(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Nested
    class BeforeTheFirstPass {

        @Test
        void listsNoProjectAndNoMeasurementTime() {
            CachedOverview cached = cache.get();

            assertThat(cached.overview().projects()).isEmpty();
            assertThat(cached.computedAt()).isNull();
        }
    }

    @Nested
    class Published {

        @Test
        void servesTheProjectsOfTheLastPassAndWhenItRan() {
            ProjectStorage project = new ProjectStorage("ws-1", "production", "prj-1", "order-service", 10L, 1, 0L);

            cache.publish(List.of(project));

            CachedOverview cached = cache.get();
            assertThat(cached.overview().projects()).containsExactly(project);
            assertThat(cached.computedAt()).isEqualTo(NOW);
        }

        @Test
        void aLaterPassReplacesTheEarlierOne() {
            cache.publish(List.of(new ProjectStorage("ws-1", "production", "gone", "gone", 10L, 1, 0L)));
            ProjectStorage remaining = new ProjectStorage("ws-1", "production", "prj-1", "order-service", 5L, 1, 0L);

            cache.publish(List.of(remaining));

            assertThat(cache.get().overview().projects()).containsExactly(remaining);
        }
    }

    @Nested
    class Measure {

        @Test
        void sumsEveryFileOfEverySession() {
            List<RecordingSession> sessions = List.of(
                    session("session-1",
                            file("recording-1.jfr", 700L, CREATED_AT),
                            file("recording-2.jfr.lz4", 300L, CREATED_AT),
                            file("heapdump.hprof.gz", 200L, CREATED_AT)),
                    session("session-2",
                            file("recording-3.jfr", 500L, LAST_ACTIVITY),
                            file("service-app.log", 30L, CREATED_AT)));

            ProjectStorage project = ProjectStorage.measure(WORKSPACE, PROJECT, sessions);

            assertThat(project).isEqualTo(new ProjectStorage(
                    "ws-1", "production", "prj-1", "order-service", 1730L, 5, LAST_ACTIVITY.toEpochMilli()));
        }

        @Test
        void reportsZeroesForAProjectWithoutSessions() {
            ProjectStorage project = ProjectStorage.measure(WORKSPACE, PROJECT, List.of());

            assertThat(project.totalSizeBytes()).isZero();
            assertThat(project.totalFiles()).isZero();
            assertThat(project.lastActivityTimeMillis()).isZero();
        }

        private static RecordingSession session(String id, RepositoryFile... files) {
            return new RecordingSession(id, id, "instance-1", CREATED_AT, false, List.of(files));
        }

        private static RepositoryFile file(String name, long size, Instant createdAt) {
            return new RepositoryFile(name, name, createdAt, size, name.contains(".jfr"), null);
        }
    }
}
