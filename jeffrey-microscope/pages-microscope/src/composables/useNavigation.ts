/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

  // Route params (with profileStore fallback for the simplified profile URL pattern)
  const serverId = computed(
    () => (route.params.serverId as string) || profileStore.serverId.value
  );
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

  const projectRoot = (sId: string, wId: string, pId: string) =>
    `/remote-servers/${sId}/workspaces/${wId}/projects/${pId}`;

  const navigateToWorkspace = (sId: string, wId: string) => {
    router.push(`/remote-servers/${sId}/workspaces/${wId}`);
  };

  const navigateToWorkspaceProjects = (sId: string, wId: string) => {
    router.push(`/remote-servers/${sId}/workspaces/${wId}/projects`);
  };

  const navigateToProject = (sId: string, pId: string, wId?: string) => {
    const targetWorkspaceId = wId || workspaceId.value;
    router.push(projectRoot(sId, targetWorkspaceId, pId));
  };

  /**
   * Navigate to a profile using simplified URL pattern.
   */
  const navigateToProfile = (prId: string) => {
    router.push(`/profiles/${prId}/overview`);
  };

  /**
   * Navigate back to the project's recordings list.
   * Uses serverId/workspaceId/projectId from profileStore when using simplified URLs.
   */
  const navigateToProjectRecordings = (sId?: string, wId?: string, pId?: string) => {
    const targetServerId = sId || serverId.value;
    const targetWorkspaceId = wId || workspaceId.value;
    const targetProjectId = pId || projectId.value;
    router.push(`${projectRoot(targetServerId, targetWorkspaceId, targetProjectId)}/recordings`);
  };

  const generateProjectUrl = (path: string, sId?: string, pId?: string, wId?: string) => {
    const targetServerId = sId || serverId.value;
    const targetWorkspaceId = wId || workspaceId.value;
    const targetProjectId = pId || projectId.value;
    return `${projectRoot(targetServerId, targetWorkspaceId, targetProjectId)}/${path}`;
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
    sId?: string,
    pId?: string,
    wId?: string
  ) => {
    const targetServerId = sId || serverId.value;
    const targetWorkspaceId = wId || workspaceId.value;
    const targetProjectId = pId || projectId.value;
    const basePath = `${projectRoot(targetServerId, targetWorkspaceId, targetProjectId)}/instances/${instId}`;
    return path ? `${basePath}/${path}` : basePath;
  };

  const buildStreamUrl = (
    path: 'events/live-stream' | 'events/replay-stream',
    sessionId?: string,
    sessionInstance?: string,
    sId?: string,
    pId?: string,
    wId?: string
  ) => {
    const base = generateProjectUrl(path, sId, pId, wId);
    if (!sessionId) return base;
    const params = new URLSearchParams();
    params.set('sessionId', sessionId);
    if (sessionInstance) params.set('sessionInstance', sessionInstance);
    return `${base}?${params.toString()}`;
  };

  const generateLiveStreamUrl = (
    sessionId?: string,
    sessionInstance?: string,
    sId?: string,
    pId?: string,
    wId?: string
  ) => buildStreamUrl('events/live-stream', sessionId, sessionInstance, sId, pId, wId);

  const generateReplayStreamUrl = (
    sessionId?: string,
    sessionInstance?: string,
    sId?: string,
    pId?: string,
    wId?: string
  ) => buildStreamUrl('events/replay-stream', sessionId, sessionInstance, sId, pId, wId);

  /**
   * Navigate to an instance detail page.
   */
  const navigateToInstance = (instId: string, sId?: string, pId?: string, wId?: string) => {
    router.push(generateInstanceUrl(instId, undefined, sId, pId, wId));
  };

  return {
    // Route params (with profileStore fallback)
    serverId,
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
    generateInstanceUrl,
    generateLiveStreamUrl,
    generateReplayStreamUrl
  };
}
