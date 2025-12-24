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

import {
  BaseJobTypePlugin,
  type JobCardMetadata,
  type JobValidationResult,
  type JobCreationParams
} from './JobTypePlugin';
import OrphanedProjectRecordingStorageCleanerModal from '@/components/scheduler/OrphanedProjectRecordingStorageCleanerModal.vue';
import type JobInfo from '@/services/model/JobInfo';

/**
 * Plugin for Orphaned Project Recording Storage Cleaner job type
 */
export class OrphanedProjectRecordingStorageCleanerPlugin extends BaseJobTypePlugin {
  readonly jobType = 'ORPHANED_PROJECT_RECORDING_STORAGE_CLEANER';

  readonly cardMetadata: JobCardMetadata = {
    jobType: 'ORPHANED_PROJECT_RECORDING_STORAGE_CLEANER',
    title: 'Orphaned Project Storage Cleaner',
    description: 'Removes orphaned projects from recording storage that no longer exist in the database, freeing up storage space.',
    icon: 'bi-trash3',
    iconColor: 'text-danger',
    iconBg: 'bg-danger-soft'
  };

  readonly modalComponent = OrphanedProjectRecordingStorageCleanerModal;

  jobExists(jobs: JobInfo[]): boolean {
    return jobs.some(job => job.jobType === this.jobType);
  }

  async validateJobCreation(params: JobCreationParams): Promise<JobValidationResult> {
    // No parameters needed for this job type, always valid
    return {
      isValid: true,
      errors: []
    };
  }

  buildJobCreationParams(formData: any): JobCreationParams {
    // Return empty object - no parameters needed
    return {};
  }

  getJobDisplayInfo(job: JobInfo) {
    return {
      title: this.cardMetadata.title,
      icon: this.cardMetadata.icon,
      iconColor: this.cardMetadata.iconColor,
      iconBg: this.cardMetadata.iconBg
    };
  }
}
