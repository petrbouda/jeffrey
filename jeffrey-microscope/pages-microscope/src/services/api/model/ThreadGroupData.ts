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

import ThreadRowData from '@/services/api/model/ThreadRowData';

/**
 * One lane on the timeline, standing for every thread that shares a name.
 *
 * `lane` is shaped exactly like a single thread's row, so a group draws the same way a thread does.
 * For a group of one it *is* that thread's row, ids included — which is what keeps the flamegraph
 * and thread-info actions working on threads that were never collapsed.
 */
export default interface ThreadGroupData {
  key: string;
  threadCount: number;
  lane: ThreadRowData;
}

/** One page of lanes, plus what the footer needs to describe the rest. */
export interface ThreadGroupResponse {
  common: import('@/services/api/model/ThreadCommon').default;
  groups: ThreadGroupData[];
  offset: number;
  matchedGroups: number;
  totalGroups: number;
  totalThreads: number;
}
