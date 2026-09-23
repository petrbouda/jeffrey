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

package cafe.jeffrey.profile.thread;

import java.util.List;

/**
 * The threads sharing one name, before their lanes are merged into a single one.
 *
 * <p>Kept apart from {@link ThreadGroup} because the two cost very different amounts. Ordering and
 * filtering the groups only needs the totals below, which are a sum over members; building the lane
 * means merging every member's bands, and that is worth doing only for the groups a page actually
 * returns.
 */
public record ThreadGroupMembers(String key, List<ThreadRow> members) {

    public ThreadGroupMembers {
        if (members.isEmpty()) {
            throw new IllegalArgumentException("A group needs at least one thread: " + key);
        }
    }

    public int threadCount() {
        return members.size();
    }

    public long eventsCount() {
        return members.stream().mapToLong(ThreadRow::eventsCount).sum();
    }

    /**
     * The longest a member was alive. A sum would exceed the recording itself once a pool has more
     * than one worker, which reads as nonsense next to a timeline.
     */
    public long totalDuration() {
        return members.stream().mapToLong(ThreadRow::totalDuration).max().orElse(0);
    }

    public boolean isSingleThread() {
        return members.size() == 1;
    }
}
