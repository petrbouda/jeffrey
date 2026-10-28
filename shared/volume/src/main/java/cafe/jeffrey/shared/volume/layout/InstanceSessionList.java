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

package cafe.jeffrey.shared.volume.layout;

import java.util.List;

/**
 * One instance with a page of its sessions, newest first, as one listing of its directory names
 * them: no session is read.
 *
 * @param hasOlder whether sessions older than the page's are left to read
 */
public record InstanceSessionList(InstanceDirectory instance, List<SessionHeader> sessions, boolean hasOlder) {

    public InstanceSessionList {
        if (instance == null || sessions == null) {
            throw new IllegalArgumentException("An instance's session list needs its instance and its sessions");
        }
        sessions = List.copyOf(sessions);
    }
}
