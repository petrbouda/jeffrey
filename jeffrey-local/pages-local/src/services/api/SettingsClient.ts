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

import BasePlatformClient from '@/services/api/BasePlatformClient';
import type Setting from '@/services/api/model/Setting';

export default class SettingsClient extends BasePlatformClient {
  constructor() {
    super('/settings');
  }

  fetchAll(): Promise<Setting[]> {
    return super.get<Setting[]>();
  }

  fetchByCategory(category: string): Promise<Setting[]> {
    return super.get<Setting[]>(`/${category}`);
  }

  upsert(category: string, name: string, value: string, secret: boolean): Promise<void> {
    return super.put<void>(`/${category}/${name}`, { value, secret });
  }

  fetchStatus(): Promise<{ restartRequired: boolean; encryptionMode: string }> {
    return super.get<{ restartRequired: boolean; encryptionMode: string }>('/status');
  }
}
