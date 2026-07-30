/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

/**
 * One drawable band on a thread's timeline, in nanoseconds from the start of the recording.
 *
 * A band covers `eventCount` source events: the backend merges events that are closer together than
 * the timeline can resolve. Merging chains, so on a busy lane one band spans the whole recording and
 * its count is that run's total — it is not a count for any position inside the band, and a tooltip
 * must not read it as one.
 *
 * The events' own fields are not part of the band. They are fetched for the slice of time under the
 * pointer, via `ProfileThreadClient.windowEvents`.
 */
export default class ThreadPeriod {
  constructor(
    public startOffset: number,
    public width: number,
    public eventCount: number
  ) {}
}
