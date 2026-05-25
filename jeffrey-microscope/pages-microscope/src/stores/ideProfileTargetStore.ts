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

import { ref } from 'vue';
import IdeClient from '@/services/api/IdeClient';
import type {
  IdeInstanceView,
  IdeTargetSelection,
  IdeTargetStatusResponse
} from '@/services/api/IdeClient';
import type { PickedTarget } from '@/stores/ideTargetPickerStore';
import ideTargetPickerStore from '@/stores/ideTargetPickerStore';
import MessageBus from '@/services/MessageBus';
import { ToastService } from '@/services/ToastService';

const NO_IDE_TITLE = 'No running IntelliJ found';
const NO_IDE_MESSAGE =
  'Open your project in IntelliJ with the Jeffrey plugin installed, then try again.';

const NOT_LINKED: IdeTargetStatusResponse = {
  selectable: false,
  linked: false,
  ideName: null,
  projectName: null,
  port: 0,
  pid: 0
};

/**
 * Profile-wide IDE link state for the nav control. The status is read cache-only (no port scan) via
 * GET /status; discovery happens only when the user explicitly selects/changes a window. Stays in
 * sync with jump-flow selections through {@link MessageBus.IDE_TARGET_CHANGED}.
 */
const status = ref<IdeTargetStatusResponse>({ ...NOT_LINKED });

let currentProfileId: string | null = null;
let listenerBound = false;

function bindListener(): void {
  if (listenerBound) {
    return;
  }
  listenerBound = true;
  MessageBus.on(MessageBus.IDE_TARGET_CHANGED, () => {
    if (currentProfileId) {
      void reload(currentProfileId);
    }
  });
}

async function reload(profileId: string): Promise<void> {
  try {
    status.value = await new IdeClient().getStatus(profileId);
  } catch {
    status.value = { ...NOT_LINKED };
  }
}

async function load(profileId: string): Promise<void> {
  currentProfileId = profileId;
  bindListener();
  await reload(profileId);
}

function toSelection(
  instances: IdeInstanceView[],
  picked: PickedTarget
): IdeTargetSelection | null {
  const instance = instances.find(i => i.port === picked.port);
  if (!instance) {
    return null;
  }
  const project = instance.projects.find(p => p.id === picked.projectId);
  if (!project) {
    return null;
  }
  return {
    port: instance.port,
    projectId: project.id,
    ideName: instance.ideName,
    projectName: project.name,
    pid: instance.pid
  };
}

/**
 * Discovers running IDE windows and lets the user pick one (always shows the picker — never
 * auto-selects, since this is an explicit change). Persists the choice and notifies listeners.
 * Returns true when a window was selected.
 */
async function selectOrChange(profileId: string): Promise<boolean> {
  const client = new IdeClient();
  const targets = await client.discoverTargets(profileId, '');
  const instances = targets.instances ?? [];
  if (instances.length === 0) {
    ToastService.warn(NO_IDE_TITLE, NO_IDE_MESSAGE);
    return false;
  }

  const picked = await ideTargetPickerStore.open(instances, targets.selectedProjectId);
  if (!picked) {
    return false;
  }

  const selection = toSelection(instances, picked);
  if (!selection) {
    return false;
  }

  await client.selectTarget(profileId, selection);
  MessageBus.emit(MessageBus.IDE_TARGET_CHANGED, null);
  return true;
}

async function disconnect(profileId: string): Promise<void> {
  await new IdeClient().clearTarget(profileId);
  MessageBus.emit(MessageBus.IDE_TARGET_CHANGED, null);
}

export default { status, load, selectOrChange, disconnect };
