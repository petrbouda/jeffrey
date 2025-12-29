/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.platform.scheduler.job.descriptor;

import pbouda.jeffrey.shared.model.job.JobType;

import java.util.Map;

/**
 * Job descriptor for the Orphaned Project Recording Storage Cleaner job.
 * This job removes projects from recording storage that no longer exist in the database.
 */
public record OrphanedProjectRecordingStorageCleanerJobDescriptor()
        implements JobDescriptor<OrphanedProjectRecordingStorageCleanerJobDescriptor> {

    @Override
    public Map<String, String> params() {
        return Map.of();
    }

    @Override
    public JobType type() {
        return JobType.ORPHANED_PROJECT_RECORDING_STORAGE_CLEANER;
    }
}
