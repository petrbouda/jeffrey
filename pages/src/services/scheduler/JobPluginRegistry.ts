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

import type { JobTypePlugin } from './plugins/JobTypePlugin';

/**
 * Registry for managing job type plugins
 */
export class JobPluginRegistry {
  private static instance: JobPluginRegistry;
  private plugins = new Map<string, JobTypePlugin>();
  private initialized = false;

  private constructor() {}

  static getInstance(): JobPluginRegistry {
    if (!JobPluginRegistry.instance) {
      JobPluginRegistry.instance = new JobPluginRegistry();
    }
    return JobPluginRegistry.instance;
  }

  /**
   * Register a job type plugin
   */
  registerPlugin(plugin: JobTypePlugin): void {
    if (this.plugins.has(plugin.jobType)) {
      console.warn(`Plugin for job type '${plugin.jobType}' is already registered`);
      return;
    }

    this.plugins.set(plugin.jobType, plugin);
    console.log(`Registered job type plugin: ${plugin.jobType}`);
  }

  /**
   * Unregister a job type plugin
   */
  unregisterPlugin(jobType: string): void {
    const plugin = this.plugins.get(jobType);
    if (plugin) {
      plugin.destroy();
      this.plugins.delete(jobType);
      console.log(`Unregistered job type plugin: ${jobType}`);
    }
  }

  /**
   * Get a specific plugin by job type
   */
  getPlugin(jobType: string): JobTypePlugin | undefined {
    return this.plugins.get(jobType);
  }

  /**
   * Get all registered plugins
   */
  getAllPlugins(): JobTypePlugin[] {
    return Array.from(this.plugins.values());
  }

  /**
   * Get all job types
   */
  getJobTypes(): string[] {
    return Array.from(this.plugins.keys());
  }

  /**
   * Check if a job type is registered
   */
  hasPlugin(jobType: string): boolean {
    return this.plugins.has(jobType);
  }

  /**
   * Initialize all registered plugins
   */
  async initializePlugins(): Promise<void> {
    if (this.initialized) {
      return;
    }

    const initPromises = Array.from(this.plugins.values()).map(plugin => 
      plugin.initialize().catch(error => {
        console.error(`Failed to initialize plugin ${plugin.jobType}:`, error);
      })
    );

    await Promise.all(initPromises);
    this.initialized = true;
    console.log(`Initialized ${this.plugins.size} job type plugins`);
  }

  /**
   * Destroy all plugins
   */
  destroy(): void {
    for (const plugin of this.plugins.values()) {
      try {
        plugin.destroy();
      } catch (error) {
        console.error(`Error destroying plugin ${plugin.jobType}:`, error);
      }
    }
    this.plugins.clear();
    this.initialized = false;
  }

  /**
   * Get plugin count
   */
  getPluginCount(): number {
    return this.plugins.size;
  }
}

// Export singleton instance
export const jobPluginRegistry = JobPluginRegistry.getInstance();