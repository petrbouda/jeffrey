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
import type { WorkspaceParams, ProjectParams, ProfileParams } from '@/types';

export function useNavigation() {
  const route = useRoute();
  const router = useRouter();

  const workspaceId = computed(() => route.params.workspaceId as string);
  const projectId = computed(() => route.params.projectId as string);
  const profileId = computed(() => route.params.profileId as string);

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

  const navigateToProfile = (profileId: string, pId?: string, wsId?: string) => {
    const targetWorkspaceId = wsId || workspaceId.value;
    const targetProjectId = pId || projectId.value;
    router.push(`/workspaces/${targetWorkspaceId}/projects/${targetProjectId}/profiles/${profileId}`);
  };

  const generateProjectUrl = (path: string, pId?: string, wsId?: string) => {
    const targetWorkspaceId = wsId || workspaceId.value;
    const targetProjectId = pId || projectId.value;
    return `/workspaces/${targetWorkspaceId}/projects/${targetProjectId}/${path}`;
  };

  const generateProfileUrl = (path: string, prId?: string, pId?: string, wsId?: string) => {
    const targetWorkspaceId = wsId || workspaceId.value;
    const targetProjectId = pId || projectId.value;
    const targetProfileId = prId || profileId.value;
    return `/workspaces/${targetWorkspaceId}/projects/${targetProjectId}/profiles/${targetProfileId}/${path}`;
  };

  return {
    // Route params
    workspaceId,
    projectId,
    profileId,

    // Navigation functions
    navigateToWorkspace,
    navigateToWorkspaceProjects,
    navigateToProject,
    navigateToProfile,

    // URL generators
    generateProjectUrl,
    generateProfileUrl,
  };
}