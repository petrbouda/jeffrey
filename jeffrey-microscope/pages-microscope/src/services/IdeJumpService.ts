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

import ideConfigStore from '@/stores/ideConfigStore';
import { ToastService } from '@/services/ToastService';

export default class IdeJumpService {
  static async openInIde(fqn: string, method: string, line: number): Promise<void> {
    const baseUrl = ideConfigStore.getBaseUrl();
    if (!baseUrl) {
      return;
    }

    const trimmed = baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl;
    const dotIdx = method.indexOf('.');
    const methodNameOnly = dotIdx >= 0 ? method.substring(dotIdx + 1) : method;
    const path = `${fqn}.${methodNameOnly}`
      .split('.')
      .map(encodeURIComponent)
      .join('.');
    const url = `${trimmed}/ide/${path}`;

    try {
      const response = await fetch(url, {
        method: 'POST',
        mode: 'cors',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ method, line })
      });
      if (!response.ok) {
        ToastService.warn('IDE jump failed', `${response.status} ${response.statusText}`);
      }
    } catch (err) {
      const detail = err instanceof Error ? err.message : String(err);
      ToastService.warn('IDE jump failed — is the IDE plugin running?', detail);
    }
  }
}
