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
import type { IdeInstanceView } from '@/services/api/IdeClient';

export interface PickedTarget {
  port: number;
  projectId: string;
}

const show = ref(false);
const instances = ref<IdeInstanceView[]>([]);
const selectedProjectId = ref<string | null>(null);

let resolver: ((target: PickedTarget | null) => void) | null = null;

/** Opens the picker and resolves once the user chooses a window or cancels (null). */
function open(insts: IdeInstanceView[], selected: string | null): Promise<PickedTarget | null> {
  instances.value = insts;
  selectedProjectId.value = selected;
  show.value = true;
  return new Promise((resolve) => {
    resolver = resolve;
  });
}

function choose(target: PickedTarget): void {
  show.value = false;
  const resolve = resolver;
  resolver = null;
  resolve?.(target);
}

function cancel(): void {
  show.value = false;
  const resolve = resolver;
  resolver = null;
  resolve?.(null);
}

export default { show, instances, selectedProjectId, open, choose, cancel };
