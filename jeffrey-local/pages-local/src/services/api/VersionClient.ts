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

import BasePlatformClient from './BasePlatformClient';

export interface UpdateCheckResponse {
  currentVersion: string;
  latestVersion: string;
  updateAvailable: boolean;
  majorUpdate: boolean;
  releaseUrl: string;
  downloadUrl: string | null;
}

export default class VersionClient extends BasePlatformClient {
  constructor() {
    super('/version');
  }

  getVersion(): Promise<string> {
    return this.get<{ version: string }>('').then(data => data.version);
  }

  checkForUpdate(): Promise<UpdateCheckResponse | null> {
    return this.get<UpdateCheckResponse>('/update-check').catch(() => null);
  }
}
