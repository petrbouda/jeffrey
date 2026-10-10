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
 * What the hub knows about a recording session's heartbeats. Shared by the instance timeline's
 * sessions and the repository's sessions list, which both carry it under `heartbeat`.
 */
export default class SessionHeartbeat {
  constructor(
    /**
     * No heartbeat arrived within the startup grace — heartbeats are not configured or not
     * emitted. Such a session is never shown as live, whatever its storage status says.
     */
    public missing: boolean,
    /** Epoch millis of the last heartbeat; only for a session still open, null when never received. */
    public lastHeartbeatAt: number | null,
    /** The Jeffrey Agent jar was written into the session directory; meaningful only when `missing`. */
    public agentPresent: boolean
  ) {}

  /** Nothing reported: neither missing nor received. What an older hub's response maps to. */
  static none(): SessionHeartbeat {
    return new SessionHeartbeat(false, null, false);
  }

  /** Maps the JSON `heartbeat` object, tolerating its absence. */
  static fromJson(data: any): SessionHeartbeat {
    if (data === null || data === undefined) {
      return SessionHeartbeat.none();
    }
    return new SessionHeartbeat(
      data.missing === true,
      typeof data.lastHeartbeatAt === 'number' ? data.lastHeartbeatAt : null,
      data.agentPresent === true
    );
  }
}
