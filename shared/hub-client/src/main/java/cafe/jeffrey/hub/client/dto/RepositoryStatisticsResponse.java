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

package cafe.jeffrey.hub.client.dto;

import cafe.jeffrey.shared.common.model.repository.RepositoryStatistics;

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
