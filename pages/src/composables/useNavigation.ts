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

import { useRoute, useRouter } from 'vue-router';
import { computed } from 'vue';
import { profileStore } from '@/stores/profileStore';

export function useNavigation() {
  const route = useRoute();
  const router = useRouter();

  // Get workspaceId and projectId from route params OR from profileStore
  // This enables working with both old nested URLs and new simplified URLs
  const workspaceId = computed(() =>
    (route.params.workspaceId as string) || profileStore.workspaceId.value
  );
  const projectId = computed(() =>
    (route.params.projectId as string) || profileStore.projectId.value
  );
  const profileId = computed(() => route.params.profileId as string);

  /**
   * Check if we're using the simplified profile URL pattern (/profiles/:profileId/...)
   */
  const isSimplifiedProfileUrl = computed(() =>
    route.path.startsWith('/profiles/') && !route.params.workspaceId
  );

  const navigateToWorkspace = (workspaceId: string) => {
    router.push(`/workspaces/${workspaceId}`);
  };

  const navigateToWorkspaceProjects = (workspaceId: string) => {
    router.push(`/workspaces/${workspaceId}/projects`);
  };

  const navigateToProject = (projectId: string, wsId?: string) => {
    const targetWorkspaceId = wsId || workspaceId.value;
    router.push(`/workspaces/${targetWorkspaceId}/projects/${projectId}`);
  };

  /**
   * Navigate to a profile using simplified URL pattern.
   */
  const navigateToProfile = (profileId: string) => {
    router.push(`/profiles/${profileId}`);
  };

  /**
   * Navigate back to the project's profiles list.
   * Uses workspaceId/projectId from profileStore when using simplified URLs.
   */
  const navigateToProjectProfiles = (wsId?: string, pId?: string) => {
    const targetWorkspaceId = wsId || workspaceId.value;
    const targetProjectId = pId || projectId.value;
    router.push(`/workspaces/${targetWorkspaceId}/projects/${targetProjectId}/profiles`);
  };

  const generateProjectUrl = (path: string, pId?: string, wsId?: string) => {
    const targetWorkspaceId = wsId || workspaceId.value;
    const targetProjectId = pId || projectId.value;
    return `/workspaces/${targetWorkspaceId}/projects/${targetProjectId}/${path}`;
  };

  /**
   * Generate a profile URL using simplified pattern.
   */
  const generateProfileUrl = (path: string, prId?: string) => {
    const targetProfileId = prId || profileId.value;
    return `/profiles/${targetProfileId}/${path}`;
  };

  return {
    // Route params (with profileStore fallback)
    workspaceId,
    projectId,
    profileId,

    // URL pattern detection
    isSimplifiedProfileUrl,

    // Navigation functions
    navigateToWorkspace,
    navigateToWorkspaceProjects,
    navigateToProject,
    navigateToProfile,
    navigateToProjectProfiles,

    // URL generators
    generateProjectUrl,
    generateProfileUrl,
  };
}
