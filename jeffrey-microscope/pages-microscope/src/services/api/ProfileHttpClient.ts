/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import BaseProfileClient from '@/services/api/BaseProfileClient';
import HttpOverviewData from '@/services/api/model/HttpOverviewData';
import HttpSingleUriData from '@/services/api/model/HttpSingleUriData';

export default class ProfileHttpClient extends BaseProfileClient {
  private readonly mode: string;

  constructor(mode: 'client' | 'server', profileId: string) {
    super(profileId, 'http/overview');
    this.mode = mode;
  }

  public getOverview(): Promise<HttpOverviewData> {
    return super.get<HttpOverviewData>('', { mode: this.mode });
  }

  public getOverviewUri(uri: string | null): Promise<HttpSingleUriData> {
    return super.get<HttpSingleUriData>('/single', { uri, mode: this.mode });
  }
}
