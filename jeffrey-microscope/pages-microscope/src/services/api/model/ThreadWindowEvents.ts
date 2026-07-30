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
