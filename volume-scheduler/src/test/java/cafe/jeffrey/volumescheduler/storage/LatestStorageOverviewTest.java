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

package cafe.jeffrey.volumescheduler.storage;

import cafe.jeffrey.volumescheduler.volume.ProjectRef;
import cafe.jeffrey.volumescheduler.volume.RecordingSession;
import cafe.jeffrey.volumescheduler.volume.RecordingSessionFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LatestStorageOverviewTest {

    private static final Instant NOW = Instant.parse("2026-08-10T10:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant LAST_ACTIVITY = Instant.parse("2026-08-09T10:00:00Z");

    private static final ProjectRef PROJECT = new ProjectRef("ws-1", "prj-1");

    private LatestStorageOverview latest;

    @BeforeEach
    void setUp() {
        latest = new LatestStorageOverview(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Nested
    class BeforeTheFirstPass {

        @Test
        void listsNoProjectAndNoMeasurementTime() {
            assertThat(latest.get()).isEqualTo(StorageOverview.NOT_MEASURED);
            assertThat(latest.get().projects()).isEmpty();
            assertThat(latest.get().measuredAt()).isEmpty();
        }
    }

    @Nested
    class Published {

        @Test
        void servesTheProjectsOfTheLastPassAndWhenItRan() {
            ProjectStorage project = new ProjectStorage(PROJECT, 10L, 1, Optional.empty());

            latest.publish(List.of(project));

            assertThat(latest.get().projects()).containsExactly(project);
            assertThat(latest.get().measuredAt()).contains(NOW);
        }

        @Test
        void aLaterPassReplacesTheEarlierOne() {
            latest.publish(List.of(new ProjectStorage(new ProjectRef("ws-1", "gone"), 10L, 1, Optional.empty())));
            ProjectStorage remaining = new ProjectStorage(PROJECT, 5L, 1, Optional.empty());

            latest.publish(List.of(remaining));

            assertThat(latest.get().projects()).containsExactly(remaining);
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

            ProjectStorage project = ProjectStorage.measure(PROJECT, sessions);

            assertThat(project).isEqualTo(new ProjectStorage(PROJECT, 1730L, 5, Optional.of(LAST_ACTIVITY)));
        }

        @Test
        void aProjectWithoutSessionsHasNothingAndNoLastActivity() {
            ProjectStorage project = ProjectStorage.measure(PROJECT, List.of());

            assertThat(project).isEqualTo(new ProjectStorage(PROJECT, 0L, 0, Optional.empty()));
        }

        private static RecordingSession session(String id, RecordingSessionFile... files) {
            return new RecordingSession(id, "instance-1", CREATED_AT, false, List.of(files));
        }

        private static RecordingSessionFile file(String name, long size, Instant createdAt) {
            return new RecordingSessionFile(name, name, createdAt, size, name.contains(".jfr"), null);
        }
    }
}
