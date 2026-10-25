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

package cafe.jeffrey.microscope.remote.client.dto;

import cafe.jeffrey.microscope.remote.client.manager.InstanceWithSessions;

import java.time.Clock;
import java.util.List;

/**
 * One instance with its sessions, newest first, each with its file count and size but not its
 * files — everything an instance's page asks for, in one response.
 */
public record InstanceWithSessionsResponse(InstanceResponse instance, List<RecordingSessionResponse> sessions) {

    public static InstanceWithSessionsResponse from(InstanceWithSessions found, Clock clock) {
        return new InstanceWithSessionsResponse(
                InstanceResponse.from(found.instance(), clock),
                found.sessions().stream()
                        .map(session -> RecordingSessionResponse.from(session, clock))
                        .toList());
    }
}
