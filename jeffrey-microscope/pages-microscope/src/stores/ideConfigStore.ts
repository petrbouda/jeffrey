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
    .then((cfg) => {
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
  isJfrProfilerMode,
};
