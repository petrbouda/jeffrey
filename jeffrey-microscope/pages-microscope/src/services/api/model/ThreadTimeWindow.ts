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

/**
 * The slice of recording time under the pointer, in nanoseconds from the start of the recording.
 *
 * This is what the client *asks* about — one pixel of the timeline converted back to time. The
 * server answers about the window it could actually resolve, which is this one snapped to the
 * millisecond grid the events are stored at; that one comes back as `ThreadWindowEvents.fromOffset`
 * and `toOffset`. Render those, not these, or a count will look finer-grained than it is.
 */
export default class ThreadTimeWindow {
  constructor(
    public readonly from: number,
    public readonly to: number
  ) {}
}
