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
import WorkspaceProfilerSettingsSynchronizerModal from '@/components/scheduler/WorkspaceProfilerSettingsSynchronizerModal.vue';
import type JobInfo from '@/services/model/JobInfo';

/**
 * Form data structure for Workspace Profiler Settings Synchronizer
 */
interface WorkspaceProfilerSettingsSynchronizerFormData {
  maxVersions: number;
}

/**
 * Plugin for Workspace Profiler Settings Synchronizer job type
 */
export class WorkspaceProfilerSettingsSynchronizerPlugin extends BaseJobTypePlugin {
  readonly jobType = 'WORKSPACE_PROFILER_SETTINGS_SYNCHRONIZER';

  readonly cardMetadata: JobCardMetadata = {
    jobType: 'WORKSPACE_PROFILER_SETTINGS_SYNCHRONIZER',
    title: 'Profiler Settings Synchronizer',
    description: 'Synchronizes profiler agent settings from the database to remote workspace storage for backup and versioning.',
    icon: 'bi-cpu',
    iconColor: 'text-orange',
    iconBg: 'bg-orange-soft'
  };

  readonly modalComponent = WorkspaceProfilerSettingsSynchronizerModal;

  jobExists(jobs: JobInfo[]): boolean {
    return jobs.some(job => job.jobType === this.jobType);
  }

  async validateJobCreation(params: JobCreationParams): Promise<JobValidationResult> {
    const errors: string[] = [];

    // Validate maxVersions parameter
    if (!params.maxVersions) {
      errors.push('Max versions is required');
    } else {
      const maxVersions = Number(params.maxVersions);

      if (isNaN(maxVersions)) {
        errors.push('Max versions must be a valid number');
      } else if (!Number.isInteger(maxVersions)) {
        errors.push('Max versions must be an integer');
      } else if (maxVersions < 1) {
        errors.push('Max versions must be at least 1');
      } else if (maxVersions > 100) {
        errors.push('Max versions cannot exceed 100');
      }
    }

    return {
      isValid: errors.length === 0,
      errors
    };
  }

  buildJobCreationParams(formData: WorkspaceProfilerSettingsSynchronizerFormData): JobCreationParams {
    return {
      maxVersions: formData.maxVersions
    };
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