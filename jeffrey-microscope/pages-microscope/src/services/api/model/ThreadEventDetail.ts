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
 * One event behind a timeline band. `values` is positional and lines up with the fields the band's
 * category declares in `ThreadMetadata` — the same contract the timeline has always used, only
 * fetched per hovered band instead of shipped with every event.
 */
export default interface ThreadEventDetail {
  startOffset: number;
  width: number;
  values: Array<string>;
}

/**
 * The timeline categories whose bands can be expanded into individual events. Mirrors the states
 * the backend accepts on `/thread/events`; the lifespan states are reconstructed from start/end
 * pairs and have nothing to look up.
 */
export type ThreadEventState =
  | 'PARKED'
  | 'BLOCKED'
  | 'WAITING'
  | 'SLEEP'
  | 'SOCKET_READ'
  | 'SOCKET_WRITE'
  | 'FILE_READ'
  | 'FILE_WRITE';
