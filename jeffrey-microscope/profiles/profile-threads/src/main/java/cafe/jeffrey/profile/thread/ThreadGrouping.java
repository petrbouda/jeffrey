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

import cafe.jeffrey.shared.common.model.ThreadInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Folds threads that share a name into one lane each.
 *
 * <p>Splitting the work in two is deliberate. {@link #group(List)} only buckets rows by key, which is
 * what ordering and filtering need; {@link #merge(ThreadGroupMembers)} builds the union lane, which
 * walks every member's bands and is therefore run only for the groups a page returns.
 */
public final class ThreadGrouping {

    /**
     * A collapsed lane has no thread of its own. The ids are the "not available" marker
     * {@link ThreadInfo} already uses, so nothing downstream mistakes the lane for a real thread.
     */
    private static final long NO_THREAD_ID = -1;

    private final ThreadBands bands;

    public ThreadGrouping(ThreadBands bands) {
        this.bands = bands;
    }

    /**
     * Buckets rows by group key, preserving the order the rows arrived in so the result is stable.
     */
    public List<ThreadGroupMembers> group(List<ThreadRow> rows) {
        Map<String, List<ThreadRow>> byKey = new LinkedHashMap<>();
        for (ThreadRow row : rows) {
            byKey.computeIfAbsent(ThreadGroupKey.of(row.threadInfo().name()), _ -> new ArrayList<>())
                    .add(row);
        }

        List<ThreadGroupMembers> groups = new ArrayList<>(byKey.size());
        byKey.forEach((key, members) -> groups.add(new ThreadGroupMembers(key, members)));
        return groups;
    }

    /**
     * Builds the lane a group draws as. A group of one keeps its thread's own row untouched — the
     * lane is that thread, so its ids stay intact and everything that acts on a thread still works.
     */
    public ThreadGroup merge(ThreadGroupMembers group) {
        if (group.isSingleThread()) {
            return new ThreadGroup(group.key(), 1, group.members().getFirst());
        }

        List<ThreadRow> members = group.members();
        ThreadRow lane = new ThreadRow(
                group.totalDuration(),
                group.eventsCount(),
                new ThreadInfo(NO_THREAD_ID, NO_THREAD_ID, group.key()),
                union(members, ThreadRow::lifespan),
                union(members, ThreadRow::parked),
                union(members, ThreadRow::blocked),
                union(members, ThreadRow::waiting),
                union(members, ThreadRow::sleep),
                union(members, ThreadRow::socketRead),
                union(members, ThreadRow::socketWrite),
                union(members, ThreadRow::fileRead),
                union(members, ThreadRow::fileWrite));

        return new ThreadGroup(group.key(), members.size(), lane);
    }

    /**
     * Every member's bands of one category on a single track. They are merged the same way a single
     * thread's events are, so bands that would overlap on screen become one and keep their count —
     * without it, a pool of 351 would put 351 rectangles on top of each other.
     */
    private List<ThreadPeriod> union(
            List<ThreadRow> members,
            Function<ThreadRow, List<ThreadPeriod>> category) {

        List<ThreadPeriod> all = new ArrayList<>();
        for (ThreadRow member : members) {
            all.addAll(category.apply(member));
        }
        all.sort(Comparator.comparingLong(ThreadPeriod::startOffset));
        return bands.merge(all);
    }
}
