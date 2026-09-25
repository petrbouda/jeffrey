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

package cafe.jeffrey.hub.core.web.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.InfrastructureUsage;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.ProjectStorage;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache.CachedOverview;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.hub.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class StorageControllerTest {

    private static final Instant COMPUTED_AT = Instant.parse("2026-08-10T10:00:00Z");

    @Mock
    StorageOverviewCache storageOverviewCache;

    @Test
    void returnsCachedStorageOverview() {
        StorageOverview overview = new StorageOverview(
                new InfrastructureUsage(2_900_000_000L, 1_300_000_000L),
                List.of(new ProjectStorage(
                        "ws-1", "production",
                        "prj-1", "order-service",
                        27_100_000_000L, 342, 1_775_000_000_000L)));
        when(storageOverviewCache.get()).thenReturn(new CachedOverview(overview, COMPUTED_AT));

        MockMvcTester mvc = mockMvcTesterFor(new StorageController(storageOverviewCache));

        assertThat(mvc.get().uri("/api/internal/storage"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.computedAtMillis", v -> assertThat(v).asNumber().isEqualTo(COMPUTED_AT.toEpochMilli()))
                .hasPathSatisfying("$.databaseSizeBytes", v -> assertThat(v).asNumber().isEqualTo(2_900_000_000L))
                .hasPathSatisfying("$.tempSizeBytes", v -> assertThat(v).asNumber().isEqualTo(1_300_000_000))
                .hasPathSatisfying("$.projects[0].workspaceName", v -> assertThat(v).asString().isEqualTo("production"))
                .hasPathSatisfying("$.projects[0].projectName", v -> assertThat(v).asString().isEqualTo("order-service"))
                .hasPathSatisfying("$.projects[0].totalSizeBytes", v -> assertThat(v).asNumber().isEqualTo(27_100_000_000L))
                .hasPathSatisfying("$.projects[0].totalFiles", v -> assertThat(v).asNumber().isEqualTo(342))
                .hasPathSatisfying("$.projects[0].lastActivityTimeMillis", v -> assertThat(v).asNumber().isEqualTo(1_775_000_000_000L))
                .doesNotHavePath("$.projects[0].fileTypes")
                .doesNotHavePath("$.projects[0].largestFiles");
    }

    @Test
    void returnsEmptyProjectsWhenNothingIsStored() {
        StorageOverview overview = new StorageOverview(
                new InfrastructureUsage(0L, 0L),
                List.of());
        when(storageOverviewCache.get()).thenReturn(new CachedOverview(overview, COMPUTED_AT));

        MockMvcTester mvc = mockMvcTesterFor(new StorageController(storageOverviewCache));

        assertThat(mvc.get().uri("/api/internal/storage"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.projects", v -> assertThat(v).asList().isEmpty());
    }
}
