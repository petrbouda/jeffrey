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

import BaseProfileClient from '@/services/api/BaseProfileClient';
import TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import type {
  FileForceStats,
  IoEndpoint,
  IoOperation,
  IoOverview
} from '@/services/api/model/IoModels';

export default class ProfileFileIoClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'io/file');
  }

  public getOverview(): Promise<IoOverview> {
    return this.get<IoOverview>('');
  }

  public getTimeline(): Promise<TimeseriesData> {
    return this.get<TimeseriesData>('/timeline');
  }

  public getSlowest(): Promise<IoOperation[]> {
    return this.get<IoOperation[]>('/slowest');
  }

  public getFiles(): Promise<IoEndpoint[]> {
    return this.get<IoEndpoint[]>('/endpoints');
  }

  public getDirectories(): Promise<IoEndpoint[]> {
    return this.get<IoEndpoint[]>('/directories');
  }

  public getForce(): Promise<FileForceStats> {
    return this.get<FileForceStats>('/force');
  }
}
