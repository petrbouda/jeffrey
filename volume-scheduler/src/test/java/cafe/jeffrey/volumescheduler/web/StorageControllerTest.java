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
package cafe.jeffrey.volumescheduler.web;

import cafe.jeffrey.volumescheduler.storage.LatestStorageOverview;
import cafe.jeffrey.volumescheduler.storage.ProjectStorage;
import cafe.jeffrey.volumescheduler.storage.StorageOverview;
import cafe.jeffrey.volumescheduler.volume.ProjectRef;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static cafe.jeffrey.volumescheduler.web.MockMvcSupport.mockMvcTesterFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@code GET /api/v1/storage}: the last pass's figures grouped by workspace, with the totals the
 * reader would otherwise have to add up, and ISO-8601 times.
 */
@ExtendWith(MockitoExtension.class)
class StorageControllerTest {

    private static final Instant MEASURED_AT = Instant.parse("2026-11-08T20:35:10Z");
    private static final Instant ORDERS_ACTIVITY = Instant.parse("2026-11-08T20:30:00Z");
    private static final long GIB = 1024L * 1024L * 1024L;

    @Mock
    LatestStorageOverview storageOverview;

    private MockMvcTester mvc() {
        return mockMvcTesterFor(new StorageController(storageOverview));
    }

    @Nested
    class Measured {

        @Test
        void groupsTheProjectsByWorkspaceWithTotalsAtBothLevels() {
            when(storageOverview.get()).thenReturn(new StorageOverview(Optional.of(MEASURED_AT), List.of(
                    new ProjectStorage(new ProjectRef("production", "orders"), 17 * GIB, 1004, Optional.of(ORDERS_ACTIVITY)),
                    new ProjectStorage(new ProjectRef("staging", "orders"), 2 * GIB, 132, Optional.of(ORDERS_ACTIVITY)),
                    new ProjectStorage(new ProjectRef("production", "payments"), 3 * GIB, 206, Optional.of(ORDERS_ACTIVITY)))));

            assertThat(mvc().get().uri("/api/v1/storage"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.measuredAt", v -> assertThat(v).asString().isEqualTo("2026-11-08T20:35:10Z"))
                    .hasPathSatisfying("$.totalBytes", v -> assertThat(v).asNumber().isEqualTo(22 * GIB))
                    .hasPathSatisfying("$.totalFiles", v -> assertThat(v).asNumber().isEqualTo(1342))
                    .hasPathSatisfying("$.workspaces.length()", v -> assertThat(v).asNumber().isEqualTo(2))
                    .hasPathSatisfying("$.workspaces[0].id", v -> assertThat(v).asString().isEqualTo("production"))
                    .hasPathSatisfying("$.workspaces[0].totalBytes", v -> assertThat(v).asNumber().isEqualTo(20 * GIB))
                    .hasPathSatisfying("$.workspaces[0].totalFiles", v -> assertThat(v).asNumber().isEqualTo(1210))
                    .hasPathSatisfying("$.workspaces[0].projects[0].id", v -> assertThat(v).asString().isEqualTo("orders"))
                    .hasPathSatisfying("$.workspaces[0].projects[1].id", v -> assertThat(v).asString().isEqualTo("payments"))
                    .hasPathSatisfying("$.workspaces[0].projects[0].lastActivityAt",
                            v -> assertThat(v).asString().isEqualTo("2026-11-08T20:30:00Z"))
                    .hasPathSatisfying("$.workspaces[1].id", v -> assertThat(v).asString().isEqualTo("staging"))
                    .hasPathSatisfying("$.workspaces[1].totalBytes", v -> assertThat(v).asNumber().isEqualTo(2 * GIB))
                    .doesNotHavePath("$.workspaces[0].projects[0].projectName")
                    .doesNotHavePath("$.projects");
        }

        @Test
        void aProjectWithoutFilesHasNoLastActivity() {
            when(storageOverview.get()).thenReturn(new StorageOverview(Optional.of(MEASURED_AT), List.of(
                    new ProjectStorage(new ProjectRef("production", "empty"), 0L, 0, Optional.empty()))));

            assertThat(mvc().get().uri("/api/v1/storage"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.workspaces[0].projects[0].lastActivityAt", v -> assertThat(v).isNull());
        }
    }

    @Nested
    class BeforeTheFirstPass {

        @Test
        void saysNothingWasMeasuredYet() {
            when(storageOverview.get()).thenReturn(StorageOverview.NOT_MEASURED);

            assertThat(mvc().get().uri("/api/v1/storage"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.measuredAt", v -> assertThat(v).isNull())
                    .hasPathSatisfying("$.totalBytes", v -> assertThat(v).asNumber().isEqualTo(0))
                    .hasPathSatisfying("$.workspaces", v -> assertThat(v).asArray().isEmpty());
        }
    }
}
