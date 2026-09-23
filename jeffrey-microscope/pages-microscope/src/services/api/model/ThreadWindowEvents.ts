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

import type ThreadEventDetail from '@/services/api/model/ThreadEventDetail';

/**
 * What one category of a lane was doing during a hovered slice of time.
 *
 * Deliberately not a description of a band: a band merges every run of activity too dense to draw
 * apart, so on a busy lane one band covers the whole recording and its count is the same wherever
 * the pointer is.
 */
export default interface ThreadWindowEvents {
  /** Start of the window the server resolved, in nanoseconds — the request snapped down to a whole millisecond. */
  fromOffset: number;
  /** End of the resolved window, exclusive, so neighbouring windows tile without double counting. */
  toOffset: number;
  /** How many events of the category start inside the window, regardless of how many were sampled. */
  eventCount: number;
  /** The first few of those events, for the tooltip's field rows. */
  events: ThreadEventDetail[];
}
