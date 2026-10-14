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

package cafe.jeffrey.hub.client.dto;

import java.util.List;

/**
 * One instance as the hub reports it alone: its lifecycle and its sessions, newest first, each
 * session with its file count and size but not its files.
 */
public record InstanceWithSessionsResponse(InstanceResponse instance, List<RecordingSessionResponse> sessions) {

    public InstanceWithSessionsResponse {
        sessions = List.copyOf(sessions);
    }
}
