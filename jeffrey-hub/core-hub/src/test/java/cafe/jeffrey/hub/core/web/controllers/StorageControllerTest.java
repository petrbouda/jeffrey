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

package cafe.jeffrey.hub.core.web.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.hub.core.manager.storage.StorageManager;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.DiskSpace;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.InfrastructureUsage;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.ProjectStorage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static cafe.jeffrey.hub.core.web.MockMvcSupport.mockMvcTesterFor;

@ExtendWith(MockitoExtension.class)
class StorageControllerTest {

    @Mock
    StorageManager storageManager;

    @Test
    void returnsStorageOverview() {
        StorageOverview overview = new StorageOverview(
                new DiskSpace(512_000_000_000L, 387_000_000_000L),
                new InfrastructureUsage(2_900_000_000L, 214_000_000L, 1_300_000_000L),
                List.of(new ProjectStorage(
                        "ws-1", "production",
                        "prj-1", "order-service", null,
                        27_100_000_000L, 342,
                        20_000_000_000L, 5_000_000_000L, 1_600_000_000L, 500_000_000L)));
        when(storageManager.overview()).thenReturn(overview);

        MockMvcTester mvc = mockMvcTesterFor(new StorageController(storageManager));

        assertThat(mvc.get().uri("/api/internal/storage"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.diskTotalBytes", v -> assertThat(v).asNumber().isEqualTo(512_000_000_000L))
                .hasPathSatisfying("$.diskUsableBytes", v -> assertThat(v).asNumber().isEqualTo(387_000_000_000L))
                .hasPathSatisfying("$.databaseSizeBytes", v -> assertThat(v).asNumber().isEqualTo(2_900_000_000L))
                .hasPathSatisfying("$.queueSizeBytes", v -> assertThat(v).asNumber().isEqualTo(214_000_000))
                .hasPathSatisfying("$.tempSizeBytes", v -> assertThat(v).asNumber().isEqualTo(1_300_000_000))
                .hasPathSatisfying("$.projects[0].workspaceName", v -> assertThat(v).asString().isEqualTo("production"))
                .hasPathSatisfying("$.projects[0].projectName", v -> assertThat(v).asString().isEqualTo("order-service"))
                .hasPathSatisfying("$.projects[0].totalSizeBytes", v -> assertThat(v).asNumber().isEqualTo(27_100_000_000L))
                .hasPathSatisfying("$.projects[0].totalFiles", v -> assertThat(v).asNumber().isEqualTo(342))
                .hasPathSatisfying("$.projects[0].jfrSizeBytes", v -> assertThat(v).asNumber().isEqualTo(20_000_000_000L));
    }

    @Test
    void returnsEmptyProjectsWhenNothingIsStored() {
        StorageOverview overview = new StorageOverview(
                DiskSpace.UNKNOWN,
                new InfrastructureUsage(0L, 0L, 0L),
                List.of());
        when(storageManager.overview()).thenReturn(overview);

        MockMvcTester mvc = mockMvcTesterFor(new StorageController(storageManager));

        assertThat(mvc.get().uri("/api/internal/storage"))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.diskTotalBytes", v -> assertThat(v).asNumber().isEqualTo(0))
                .hasPathSatisfying("$.projects", v -> assertThat(v).asList().isEmpty());
    }
}
