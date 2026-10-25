/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.shared.common.model.repository;

/**
 * Session metadata persisted as {@code .session-info.json} in the session directory. The hub
 * reads the session's id and instance from the directories it sits in and its start time and
 * time zone from here; a session's order in its instance is the order of these start times. Files
 * written by older provisioners carry more fields, which are ignored, and no zone.
 *
 * @param sessionId identifier of the session, equal to its directory name
 * @param createdAt when the session was provisioned (epoch millis)
 * @param zoneId    the time zone of the machine the profiled JVM runs on, as a {@link java.time.ZoneId}
 *                  id: async-profiler writes the {@code %t} in a recording's name in that local time,
 *                  so the hub needs it to read the name back as an instant. {@code null} in a marker
 *                  written before the zone was recorded, which the hub reads as UTC.
 */
public record RemoteProjectInstanceSession(
        String sessionId,
        long createdAt,
        String zoneId) {
}
