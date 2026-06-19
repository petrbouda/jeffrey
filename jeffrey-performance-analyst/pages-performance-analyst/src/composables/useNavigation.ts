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

/**
 * Analyst-local navigation composable. Unlike Microscope's, it has no profileStore coupling and
 * no profile/stream destinations beyond the URL generators the shared instance views reference.
 */
export function useNavigation() {
  const route = useRoute();
  const router = useRouter();

  const hubId = computed(() => route.params.hubId as string);
  const workspaceId = computed(() => route.params.workspaceId as string);
  const projectId = computed(() => route.params.projectId as string);
  const instanceId = computed(() => route.params.instanceId as string);

  const projectRoot = (sId: string, wId: string, pId: string) =>
    `/hubs/${sId}/workspaces/${wId}/projects/${pId}`;

  const generateProjectUrl = (path: string, sId?: string, pId?: string, wId?: string) => {
    const targetServerId = sId || hubId.value;
    const targetWorkspaceId = wId || workspaceId.value;
    const targetProjectId = pId || projectId.value;
    return `${projectRoot(targetServerId, targetWorkspaceId, targetProjectId)}/${path}`;
  };

  const generateInstanceUrl = (
    instId: string,
    path?: string,
    sId?: string,
    pId?: string,
    wId?: string
  ) => {
    const targetServerId = sId || hubId.value;
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
    if (!sessionId) {
      return base;
    }
    const params = new URLSearchParams();
    params.set('sessionId', sessionId);
    if (sessionInstance) {
      params.set('sessionInstance', sessionInstance);
    }
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

  const navigateToInstance = (instId: string, sId?: string, pId?: string, wId?: string) => {
    router.push(generateInstanceUrl(instId, undefined, sId, pId, wId));
  };

  return {
    hubId,
    workspaceId,
    projectId,
    instanceId,
    generateProjectUrl,
    generateInstanceUrl,
    generateLiveStreamUrl,
    generateReplayStreamUrl,
    navigateToInstance
  };
}
