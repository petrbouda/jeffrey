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

import type { Component } from 'vue';
import type JobInfo from '@/services/model/JobInfo';

/**
 * Job card metadata for displaying in the job types grid
 */
export interface JobCardMetadata {
  jobType: string;
  title: string;
  description: string;
  icon: string;
  iconColor: string;
  iconBg: string;
}

/**
 * Job validation result
 */
export interface JobValidationResult {
  isValid: boolean;
  errors: string[];
}

/**
 * Job creation parameters
 */
export interface JobCreationParams {
  [key: string]: any;
}

/**
 * Main plugin interface for job types
 */
export interface JobTypePlugin {
  /**
   * Unique identifier for this job type
   */
  readonly jobType: string;

  /**
   * Metadata for displaying the job card
   */
  readonly cardMetadata: JobCardMetadata;

  /**
   * Modal component for job creation
   */
  readonly modalComponent: Component;

  /**
   * Check if this job type already exists in the given job list
   */
  jobExists(jobs: JobInfo[]): boolean;

  /**
   * Validate job creation parameters
   */
  validateJobCreation(params: JobCreationParams): Promise<JobValidationResult>;

  /**
   * Build API parameters for job creation
   */
  buildJobCreationParams(formData: any): JobCreationParams;

  /**
   * Get display information for an existing job
   */
  getJobDisplayInfo(job: JobInfo): {
    title: string;
    icon: string;
    iconColor: string;
    iconBg: string;
  };

  /**
   * Initialize the plugin (load dependencies, setup, etc.)
   */
  initialize(): Promise<void>;

  /**
   * Cleanup plugin resources
   */
  destroy(): void;
}

/**
 * Base abstract class for job plugins
 */
export abstract class BaseJobTypePlugin implements JobTypePlugin {
  abstract readonly jobType: string;
  abstract readonly cardMetadata: JobCardMetadata;
  abstract readonly modalComponent: Component;

  jobExists(jobs: JobInfo[]): boolean {
    return jobs.some(job => job.jobType === this.jobType);
  }

  abstract validateJobCreation(params: JobCreationParams): Promise<JobValidationResult>;
  abstract buildJobCreationParams(formData: any): JobCreationParams;

  getJobDisplayInfo(job: JobInfo) {
    return {
      title: this.cardMetadata.title,
      icon: this.cardMetadata.icon,
      iconColor: this.cardMetadata.iconColor,
      iconBg: this.cardMetadata.iconBg
    };
  }

  async initialize(): Promise<void> {
    // Default implementation - override if needed
  }

  destroy(): void {
    // Default implementation - override if needed
  }
}