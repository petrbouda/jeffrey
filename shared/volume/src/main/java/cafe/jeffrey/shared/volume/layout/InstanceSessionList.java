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
 * One instance with a page of its sessions, newest first, read together so the instance's newest
 * session and the page it is read with can never disagree.
 *
 * @param hasOlder whether sessions older than the page's are left to read
 */
public record InstanceSessionList(InstanceSummary summary, List<VolumeSession> sessions, boolean hasOlder) {

    public InstanceSessionList {
        if (summary == null || sessions == null) {
            throw new IllegalArgumentException("An instance's session list needs its summary and its sessions");
        }
        sessions = List.copyOf(sessions);
    }
}
