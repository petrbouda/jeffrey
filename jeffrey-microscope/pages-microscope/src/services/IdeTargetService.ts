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
        pid: instance.pid,
        hasClass: project.hasClass
      }))
    );
  }
}
