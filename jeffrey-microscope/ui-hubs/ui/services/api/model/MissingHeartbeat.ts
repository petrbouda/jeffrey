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
 * Marks a session the hub finished because no heartbeat arrived within the startup grace.
 * Shared by the instance timeline's sessions and the repository's sessions list, which both
 * carry it under `missingHeartbeat`; absent (null) for every other session.
 */
export default class MissingHeartbeat {
  constructor(
    /** The Jeffrey Agent jar was written into the session directory. */
    public agentPresent: boolean
  ) {}

  /** Maps the JSON `missingHeartbeat` object; null or absent means the heartbeat was not missing. */
  static fromJson(data: any): MissingHeartbeat | null {
    if (data === null || data === undefined) {
      return null;
    }
    return new MissingHeartbeat(data.agentPresent === true);
  }
}
