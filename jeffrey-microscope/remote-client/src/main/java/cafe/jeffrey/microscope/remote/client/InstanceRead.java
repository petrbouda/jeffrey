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

package cafe.jeffrey.microscope.remote.client;

import cafe.jeffrey.microscope.remote.client.dto.InstanceResponse;
import cafe.jeffrey.microscope.remote.client.dto.RecordingSessionResponse;

import java.util.List;

/**
 * One instance as the remote read it: its lifecycle and the page of its sessions asked for, newest
 * first, each with its file count and size, and its files when the page asked for them.
 *
 * @param hasOlder whether sessions older than the page's are left to read
 */
public record InstanceRead(InstanceResponse instance, List<RecordingSessionResponse> sessions, boolean hasOlder) {

    public InstanceRead {
        sessions = List.copyOf(sessions);
    }
}
