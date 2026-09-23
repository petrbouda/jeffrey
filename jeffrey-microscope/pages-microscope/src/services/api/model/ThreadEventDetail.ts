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
