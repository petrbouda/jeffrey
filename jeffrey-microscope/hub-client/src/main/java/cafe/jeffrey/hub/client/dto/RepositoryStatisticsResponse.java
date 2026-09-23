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

package cafe.jeffrey.hub.client.dto;

import cafe.jeffrey.microscope.model.repository.RepositoryStatistics;

/**
 * What a project's repository occupies on the hub, in bytes. See {@link RepositoryStatistics}
 * for why it is only that.
 */
public record RepositoryStatisticsResponse(long totalSize) {

    public static RepositoryStatisticsResponse from(RepositoryStatistics stats) {
        return new RepositoryStatisticsResponse(stats.totalSizeBytes());
    }

    public static RepositoryStatistics from(RepositoryStatisticsResponse response) {
        return new RepositoryStatistics(response.totalSize());
    }
}
