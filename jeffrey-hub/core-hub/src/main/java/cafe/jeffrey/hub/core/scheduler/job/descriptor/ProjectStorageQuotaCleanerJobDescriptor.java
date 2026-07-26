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

package cafe.jeffrey.hub.core.scheduler.job.descriptor;

import cafe.jeffrey.shared.common.model.job.JobType;

import java.util.Map;

/**
 * Disk budget a single project's repository may occupy before the quota cleaner
 * starts reclaiming its oldest data.
 *
 * @param maxSizeBytes the per-project budget in bytes; must be positive
 */
public record ProjectStorageQuotaCleanerJobDescriptor(
        long maxSizeBytes
) implements JobDescriptor<ProjectStorageQuotaCleanerJobDescriptor> {

    private static final String PARAM_MAX_SIZE = "max-size";

    public ProjectStorageQuotaCleanerJobDescriptor {
        if (maxSizeBytes <= 0) {
            throw new IllegalArgumentException("maxSizeBytes must be positive: " + maxSizeBytes);
        }
    }

    @Override
    public Map<String, String> params() {
        return Map.of(PARAM_MAX_SIZE, String.valueOf(maxSizeBytes));
    }

    @Override
    public JobType type() {
        return JobType.PROJECT_STORAGE_QUOTA_CLEANER;
    }

    public static ProjectStorageQuotaCleanerJobDescriptor of(Map<String, String> params) {
        return new ProjectStorageQuotaCleanerJobDescriptor(
                JobDescriptorUtils.resolveBytes(params, PARAM_MAX_SIZE));
    }
}
