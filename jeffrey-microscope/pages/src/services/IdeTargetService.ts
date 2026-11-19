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

import IdeClient from '@/services/api/IdeClient';
import type { IdeInstanceView, IdeTargetSelection } from '@/services/api/IdeClient';
import ideTargetPickerStore from '@/stores/ideTargetPickerStore';
import type { PickedTarget } from '@/stores/ideTargetPickerStore';
import MessageBus from '@/services/MessageBus';

export type ResolveReason = 'cancelled' | 'no-ide';

export interface ResolveResult {
  target: PickedTarget | null;
  reason?: ResolveReason;
}

/**
 * Resolves which IDE window to use for a profile, picking once and caching the choice. Auto-selects
 * when there is a single window or a single window containing the class; otherwise shows the
 * grouped-by-instance picker. The chosen window is persisted server-side (per profile).
 */
export default class IdeTargetService {
  static async resolve(profileId: string, fqn: string): Promise<ResolveResult> {
    const client = new IdeClient();
    const targets = await client.discoverTargets(profileId, fqn);
    const instances = targets.instances ?? [];
    const all = IdeTargetService.flatten(instances);

    if (all.length === 0) {
      return { target: null, reason: 'no-ide' };
    }

    // A previously cached choice that is still open wins — no prompt.
    if (targets.selectedProjectId) {
      const cached = all.find(t => t.projectId === targets.selectedProjectId);
      if (cached) {
        return { target: { port: cached.port, projectId: cached.projectId } };
      }
    }

    // Auto-select when there is no real choice to make.
    const matches = all.filter(t => t.hasClass);
    const auto = all.length === 1 ? all[0] : matches.length === 1 ? matches[0] : null;
    if (auto) {
      await IdeTargetService.persist(client, profileId, auto);
      return { target: { port: auto.port, projectId: auto.projectId } };
    }

    // Ambiguous — let the user pick once.
    const picked = await ideTargetPickerStore.open(instances, targets.selectedProjectId);
    if (!picked) {
      return { target: null, reason: 'cancelled' };
    }
    const selected = all.find(t => t.port === picked.port && t.projectId === picked.projectId);
    if (selected) {
      await IdeTargetService.persist(client, profileId, selected);
    }
    return { target: picked };
  }

  private static async persist(
    client: IdeClient,
    profileId: string,
    selection: IdeTargetSelection
  ): Promise<void> {
    await client.selectTarget(profileId, selection);
    MessageBus.emit(MessageBus.IDE_TARGET_CHANGED, null);
  }

  private static flatten(
    instances: IdeInstanceView[]
  ): Array<IdeTargetSelection & { hasClass: boolean }> {
    return instances.flatMap(instance =>
      instance.projects.map(project => ({
        port: instance.port,
        projectId: project.id,
        ideName: instance.ideName,
        projectName: project.name,
        // Sent with the selection so the link records which checkout on disk it means, not just
        // which window: that is what the ide_ MCP tools resolve a frame against.
        basePath: project.basePath,
        pid: instance.pid,
        hasClass: project.hasClass
      }))
    );
  }
}
