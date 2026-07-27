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
 * A band covers `eventCount` source events: the backend merges events that are closer together
 * than the timeline can resolve. The events' own fields are not part of the band — they are
 * fetched for the hovered band only, via `ProfileThreadClient.bandEvents`.
 */
export default class ThreadPeriod {
  constructor(
    public startOffset: number,
    public width: number,
    public eventCount: number
  ) {}
}
