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
import ThreadResponse from '@/services/api/model/ThreadResponse';
import type { ThreadSort } from '@/services/api/model/ThreadResponse';
import ThreadStatisticsResponse from '@/services/api/model/ThreadStatisticsResponse';
import Serie from '@/services/timeseries/model/Serie';
import type { ParsedDump, ThreadDumpAnalysis } from '@/services/api/model/ThreadDumpModels';
import type ReservedStackActivation from '@/services/api/model/ReservedStackActivation';
import type ThreadEventDetail from '@/services/api/model/ThreadEventDetail';
import type { ThreadEventState } from '@/services/api/model/ThreadEventDetail';
import type ThreadInfo from '@/services/api/model/ThreadInfo';
import type ThreadPeriod from '@/services/api/model/ThreadPeriod';
import type { ThreadGroupResponse } from '@/services/api/model/ThreadGroupData';

export default class ProfileThreadClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'thread');
  }

  /**
   * One page of lanes — a lane per group of identically named threads — ordered and filtered by the
   * server. Both have to happen there: a client that holds a single page can only sort and filter
   * that page.
   */
  public list(page: {
    sort: ThreadSort;
    nameFilter?: string;
    offset?: number;
    limit?: number;
  }): Promise<ThreadGroupResponse> {
    return this.get<ThreadGroupResponse>('', {
      sort: page.sort,
      nameFilter: page.nameFilter ?? '',
      offset: page.offset ?? 0,
      limit: page.limit ?? 50
    });
  }

  /**
   * The threads behind one collapsed lane. Paged like the lanes themselves, because opening a pool
   * of 351 workers would otherwise put 351 lanes on the page.
   */
  public members(page: {
    group: string;
    sort: ThreadSort;
    offset?: number;
    limit?: number;
  }): Promise<ThreadResponse> {
    return this.get<ThreadResponse>('/members', {
      group: page.group,
      sort: page.sort,
      offset: page.offset ?? 0,
      limit: page.limit ?? 50
    });
  }

  /**
   * The events behind a single timeline band, for its tooltip. The timeline itself carries only
   * rectangles and event counts, so the fields are fetched for the band under the pointer.
   *
   * A band on a collapsed lane belongs to the group rather than to a thread, so it names the group
   * instead: the lane's own {@link ThreadInfo} carries placeholder ids that match no real thread.
   */
  public bandEvents(
    threadInfo: ThreadInfo,
    state: ThreadEventState,
    band: ThreadPeriod,
    limit = 1,
    group: string | null = null
  ): Promise<ThreadEventDetail[]> {
    const target = group ? { group: group } : { osId: threadInfo.osId, javaId: threadInfo.javaId };
    return this.get<ThreadEventDetail[]>('/events', {
      ...target,
      state: state,
      from: band.startOffset,
      to: band.startOffset + band.width,
      limit: limit
    });
  }

  public statistics(): Promise<ThreadStatisticsResponse> {
    return this.get<ThreadStatisticsResponse>('/statistics');
  }

  public timeseries(): Promise<Serie> {
    return this.get<Serie>('/timeseries');
  }

  public dumps(): Promise<ThreadDumpAnalysis> {
    return this.get<ThreadDumpAnalysis>('/dumps');
  }

  public dump(index: number): Promise<ParsedDump> {
    return this.get<ParsedDump>(`/dumps/${index}`);
  }

  public reservedStack(): Promise<ReservedStackActivation[]> {
    return this.get<ReservedStackActivation[]>('/reserved-stack');
  }
}
