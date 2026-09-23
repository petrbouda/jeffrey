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

import type ThreadRowData from '@/services/api/model/ThreadRowData';
import type ThreadPeriod from '@/services/api/model/ThreadPeriod';
import type ThreadMetadata from '@/services/api/model/ThreadMetadata';
import type EventMetadata from '@/services/api/model/EventMetadata';
import type { ThreadEventState } from '@/services/api/model/ThreadEventDetail';

/**
 * What a timeline lane is made of: which categories exist, what colour each is painted, where its
 * periods live on a row and which metadata labels it.
 *
 * Kept apart from the canvas renderer on purpose. This is the answer to "what is on this thread",
 * which the breakdown panel and the details modal need as much as the renderer does — and neither of
 * them should have to pull in Konva, a tooltip and the router to ask it.
 */

export const LIFESPAN_COLOR = 'rgb(96,175,96)';
export const PARKED_COLOR = 'rgb(198,193,193)';
export const BLOCKED_COLOR = 'rgb(236,204,116)';
export const WAITING_COLOR = 'rgb(134,173,225)';
export const SLEEP_COLOR = 'rgb(65,126,228)';
export const SOCKET_READ_COLOR = 'rgb(228,33,33)';
export const SOCKET_WRITE_COLOR = 'rgb(241,135,168)';
export const FILE_READ_COLOR = 'rgb(215,33,228)';
export const FILE_WRITE_COLOR = 'rgb(210,132,236)';

/**
 * One band category: where its periods live on a row, which colour draws it, which metadata labels
 * it, and which state the backend knows it by when its events are fetched.
 */
export interface ThreadCategory {
  state: ThreadEventState;
  color: string;
  periods: (row: ThreadRowData) => ThreadPeriod[];
  metadata: (metadata: ThreadMetadata) => EventMetadata;
}

/**
 * Every category, in the order a lane draws them — which is also the order they stack in, so a busy
 * category further down this list paints over the quieter ones above it.
 */
export const THREAD_CATEGORIES: readonly ThreadCategory[] = [
  {
    state: 'PARKED',
    color: PARKED_COLOR,
    periods: row => row.parked,
    metadata: metadata => metadata.parked
  },
  {
    state: 'BLOCKED',
    color: BLOCKED_COLOR,
    periods: row => row.blocked,
    metadata: metadata => metadata.blocked
  },
  {
    state: 'WAITING',
    color: WAITING_COLOR,
    periods: row => row.waiting,
    metadata: metadata => metadata.waiting
  },
  {
    state: 'SLEEP',
    color: SLEEP_COLOR,
    periods: row => row.sleep,
    metadata: metadata => metadata.sleep
  },
  {
    state: 'SOCKET_READ',
    color: SOCKET_READ_COLOR,
    periods: row => row.socketRead,
    metadata: metadata => metadata.socketRead
  },
  {
    state: 'SOCKET_WRITE',
    color: SOCKET_WRITE_COLOR,
    periods: row => row.socketWrite,
    metadata: metadata => metadata.socketWrite
  },
  {
    state: 'FILE_READ',
    color: FILE_READ_COLOR,
    periods: row => row.fileRead,
    metadata: metadata => metadata.fileRead
  },
  {
    state: 'FILE_WRITE',
    color: FILE_WRITE_COLOR,
    periods: row => row.fileWrite,
    metadata: metadata => metadata.fileWrite
  }
];

/**
 * The categories a row actually has events for, in draw order. A thread that only ever read from a
 * socket has one.
 */
export function presentCategories(row: ThreadRowData): ThreadCategory[] {
  return THREAD_CATEGORIES.filter(category => category.periods(row).length > 0);
}

/**
 * How many events a category stands for across a whole row. A band can cover several events the
 * timeline cannot draw apart, so this is not the number of bands.
 */
export function categoryEventCount(periods: ThreadPeriod[]): number {
  return periods.reduce((total, period) => total + period.eventCount, 0);
}

/**
 * How much of the recording a category's bands cover, in nanoseconds.
 *
 * An upper bound on the time spent in that state rather than the figure itself: bands merge events
 * too close together to draw apart, and a merged band spans the gaps it bridged. Anything rendering
 * this has to say so.
 */
export function categoryCoverageNanos(periods: ThreadPeriod[]): number {
  return periods.reduce((total, period) => total + period.width, 0);
}
