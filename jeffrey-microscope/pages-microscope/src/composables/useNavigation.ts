/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { useRoute, useRouter } from 'vue-router';
import { computed } from 'vue';
import { profileStore } from '@/stores/profileStore';
import { profileLandingRoute } from '@/services/ProfileLandingRoute';

export function useNavigation() {
  const route = useRoute();
  const router = useRouter();

  // Route params (with profileStore fallback for the simplified profile URL pattern)
  const hubId = computed(() => (route.params.hubId as string) || profileStore.hubId.value);
  const workspaceId = computed(
    () => (route.params.workspaceId as string) || profileStore.workspaceId.value
  );
  const projectId = computed(
    () => (route.params.projectId as string) || profileStore.projectId.value
  );
  const profileId = computed(() => route.params.profileId as string);
  const instanceId = computed(() => route.params.instanceId as string);

  /**
   * Check if we're using the simplified profile URL pattern (/profiles/:profileId/...)
   */
  const isSimplifiedProfileUrl = computed(
    () => route.path.startsWith('/profiles/') && !route.params.workspaceId
  );

  const projectRoot = (hId: string, wId: string, pId: string) =>
    `/hubs/${hId}/workspaces/${wId}/projects/${pId}`;

  const navigateToWorkspace = (hId: string, wId: string) => {
    router.push(`/hubs/${hId}/workspaces/${wId}`);
  };

  const navigateToWorkspaceProjects = (hId: string, wId: string) => {
    router.push(`/hubs/${hId}/workspaces/${wId}/projects`);
  };

  const navigateToProject = (hId: string, pId: string, wId?: string) => {
    const targetWorkspaceId = wId || workspaceId.value;
    router.push(projectRoot(hId, targetWorkspaceId, pId));
  };

  /**
   * Navigate to a profile using simplified URL pattern.
   */
  const navigateToProfile = (prId: string, eventSource?: string | null) => {
    router.push(profileLandingRoute(prId, eventSource));
  };

  /**
   * Navigate back to the project's recordings list.
   * Uses hubId/workspaceId/projectId from profileStore when using simplified URLs.
   */
  const navigateToProjectRecordings = (hId?: string, wId?: string, pId?: string) => {
    const targetHubId = hId || hubId.value;
    const targetWorkspaceId = wId || workspaceId.value;
    const targetProjectId = pId || projectId.value;
    router.push(`${projectRoot(targetHubId, targetWorkspaceId, targetProjectId)}/recordings`);
  };

  const generateProjectUrl = (path: string, hId?: string, pId?: string, wId?: string) => {
    const targetHubId = hId || hubId.value;
    const targetWorkspaceId = wId || workspaceId.value;
    const targetProjectId = pId || projectId.value;
    return `${projectRoot(targetHubId, targetWorkspaceId, targetProjectId)}/${path}`;
  };

  /**
   * Generate a profile URL using simplified pattern.
   */
  const generateProfileUrl = (path: string, prId?: string) => {
    const targetProfileId = prId || profileId.value;
    return `/profiles/${targetProfileId}/${path}`;
  };

  /**
   * Generate an instance URL within a project.
   */
  const generateInstanceUrl = (
    instId: string,
    path?: string,
    hId?: string,
    pId?: string,
    wId?: string
  ) => {
    const targetHubId = hId || hubId.value;
    const targetWorkspaceId = wId || workspaceId.value;
    const targetProjectId = pId || projectId.value;
    const basePath = `${projectRoot(targetHubId, targetWorkspaceId, targetProjectId)}/instances/${instId}`;
    return path ? `${basePath}/${path}` : basePath;
  };

  /**
   * Navigate to an instance detail page.
   */
  const navigateToInstance = (instId: string, hId?: string, pId?: string, wId?: string) => {
    router.push(generateInstanceUrl(instId, undefined, hId, pId, wId));
  };

  return {
    // Route params (with profileStore fallback)
    hubId,
    workspaceId,
    projectId,
    profileId,
    instanceId,

    // URL pattern detection
    isSimplifiedProfileUrl,

    // Navigation functions
    navigateToWorkspace,
    navigateToWorkspaceProjects,
    navigateToProject,
    navigateToProfile,
    navigateToProjectRecordings,
    navigateToInstance,

    // URL generators
    generateProjectUrl,
    generateProfileUrl,
    generateInstanceUrl
  };
}
