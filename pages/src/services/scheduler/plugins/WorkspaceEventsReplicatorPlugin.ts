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
import WorkspaceEventsReplicatorModal from '@/components/scheduler/WorkspaceEventsReplicatorModal.vue';
import type JobInfo from '@/services/model/JobInfo';

/**
 * Plugin for Workspace Events Replicator job type
 */
export class WorkspaceEventsReplicatorPlugin extends BaseJobTypePlugin {
  readonly jobType = 'WORKSPACE_EVENTS_REPLICATOR';

  readonly cardMetadata: JobCardMetadata = {
    jobType: 'WORKSPACE_EVENTS_REPLICATOR',
    title: 'Workspace Events Replicator',
    description: 'Replicates filesystem events (project and session creation) from remote storage into the workspace event log for tracking and audit purposes.',
    icon: 'bi-database-up',
    iconColor: 'text-teal',
    iconBg: 'bg-teal-soft'
  };

  readonly modalComponent = WorkspaceEventsReplicatorModal;

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
