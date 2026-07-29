/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
