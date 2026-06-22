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

import { ref, type Ref } from 'vue';
import SettingsClient from '@/services/api/SettingsClient';

// Shared "a setting changed that only takes effect after a restart" flag. Set by the Settings page and
// the Claude Code enable toast; read by the header indicator. The backend tracks the same flag per
// process (SettingsManager.isRestartRequired), so it survives page reloads until the app restarts.
const restartRequired = ref(false);

export function useRestartRequired(): Ref<boolean> {
  return restartRequired;
}

export function markRestartRequired(): void {
  restartRequired.value = true;
}

export async function refreshRestartRequired(): Promise<void> {
  try {
    const status = await new SettingsClient().fetchStatus();
    restartRequired.value = status.restartRequired;
  } catch {
    // best-effort: leave the current value on failure
  }
}
