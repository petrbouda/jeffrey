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
 * A session as an instance's page names it, nothing of it read: when it started — off its name —
 * and whether it is its instance's newest. What it holds is a `RecordingSession`, read when it is
 * opened.
 */
export default class RecordingSessionHeader {
  constructor(
    public id: string,
    public instanceId: string,
    public createdAt: number,
    /** Its instance's newest session: no newer one has started there, so it may still grow. */
    public newest: boolean
  ) {}
}
