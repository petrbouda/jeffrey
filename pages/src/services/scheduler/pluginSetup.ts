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

import { jobPluginRegistry } from './JobPluginRegistry';
import { ProjectsSynchronizerPlugin } from './plugins/ProjectsSynchronizerPlugin';
import { WorkspaceProfilerSettingsSynchronizerPlugin } from './plugins/WorkspaceProfilerSettingsSynchronizerPlugin';
import { WorkspaceEventsReplicatorPlugin } from './plugins/WorkspaceEventsReplicatorPlugin';

/**
 * Register all available job type plugins
 */
export async function setupJobPlugins() {
  // Register the Projects Synchronizer plugin
  const projectsSyncPlugin = new ProjectsSynchronizerPlugin();
  jobPluginRegistry.registerPlugin(projectsSyncPlugin);

  // Register the Workspace Profiler Settings Synchronizer plugin
  const profilerSettingsSyncPlugin = new WorkspaceProfilerSettingsSynchronizerPlugin();
  jobPluginRegistry.registerPlugin(profilerSettingsSyncPlugin);

  // Register the Workspace Events Replicator plugin
  const eventsReplicatorPlugin = new WorkspaceEventsReplicatorPlugin();
  jobPluginRegistry.registerPlugin(eventsReplicatorPlugin);

  // TODO: Register additional plugins here as they are created
  // Example:
  // const dataCleanupPlugin = new DataCleanupPlugin();
  // jobPluginRegistry.registerPlugin(dataCleanupPlugin);

  // Initialize all registered plugins
  await jobPluginRegistry.initializePlugins();

  return jobPluginRegistry.getAllPlugins();
}