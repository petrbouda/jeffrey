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
  return new Promise(resolve => {
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
