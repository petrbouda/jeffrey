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
import IdeConfigClient from '@/services/api/IdeConfigClient';
import IdeConfig, { IdeMode } from '@/services/api/model/IdeConfig';

const config = ref<IdeConfig | null>(null);
let loadPromise: Promise<void> | null = null;

function loadOnce(): Promise<void> {
  if (loadPromise) {
    return loadPromise;
  }
  loadPromise = new IdeConfigClient()
    .getConfig()
    .then(cfg => {
      config.value = cfg;
    })
    .catch(() => {
      config.value = { enabled: false, mode: IdeMode.JEFFREY_PLUGIN };
    });
  return loadPromise;
}

function isEnabled(): boolean {
  return config.value?.enabled === true;
}

function isJfrProfilerMode(): boolean {
  return config.value?.mode === IdeMode.JFR_PROFILER_PLUGIN;
}

export default {
  loadOnce,
  isEnabled,
  isJfrProfilerMode
};
