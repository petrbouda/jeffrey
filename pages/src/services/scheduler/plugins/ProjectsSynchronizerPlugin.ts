/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
import ProjectsSynchronizerModal from '@/components/scheduler/ProjectsSynchronizerModal.vue';
import type JobInfo from '@/services/model/JobInfo';

/**
 * Form data structure for Projects Synchronizer
 */
interface ProjectsSynchronizerFormData {
  selectedTemplate?: string | null;
}

/**
 * Plugin for Projects Synchronizer job type
 */
export class ProjectsSynchronizerPlugin extends BaseJobTypePlugin {
  readonly jobType = 'PROJECTS_SYNCHRONIZER';
  
  readonly cardMetadata: JobCardMetadata = {
    jobType: 'PROJECTS_SYNCHRONIZER',
    title: 'Projects Synchronization',
    description: 'Keeps Jeffrey projects in sync with your workspace directories by auto-discovering new projects and sessions.',
    icon: 'bi-arrow-repeat',
    iconColor: 'text-purple',
    iconBg: 'bg-purple-soft'
  };

  readonly modalComponent = ProjectsSynchronizerModal;

  jobExists(jobs: JobInfo[]): boolean {
    return jobs.some(job => job.jobType === this.jobType);
  }

  async validateJobCreation(params: JobCreationParams): Promise<JobValidationResult> {
    const errors: string[] = [];

    // Projects Synchronizer has minimal validation requirements
    // All parameters are optional in the current implementation

    // You can add template validation here if needed
    if (params.templateId && typeof params.templateId !== 'string') {
      errors.push('Template ID must be a string');
    }

    return {
      isValid: errors.length === 0,
      errors
    };
  }

  buildJobCreationParams(formData: ProjectsSynchronizerFormData): JobCreationParams {
    const params: JobCreationParams = {};

    // Add templateId to params if a template is selected
    if (formData.selectedTemplate) {
      params.templateId = formData.selectedTemplate;
    }

    return params;
  }

  getJobDisplayInfo(job: JobInfo) {
    return {
      title: this.cardMetadata.title,
      icon: this.cardMetadata.icon,
      iconColor: this.cardMetadata.iconColor,
      iconBg: this.cardMetadata.iconBg
    };
  }

  async initialize(): Promise<void> {
    console.log('ProjectsSynchronizerPlugin initialized');
  }

  destroy(): void {
    console.log('ProjectsSynchronizerPlugin destroyed');
  }
}