/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
