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

/**
 * One lane on the timeline, standing for every thread that shares a name.
 *
 * <p>The lane carries the union of its members' activity, so a pool of 351 workers that each emitted
 * a single event draws as 351 marks on one row — which is the cadence the pool was running at, and
 * the thing a row per worker made impossible to see.
 *
 * @param key         what members are fetched by; see {@link ThreadGroupKey}
 * @param threadCount how many threads the lane stands for
 * @param lane        the merged activity, shaped exactly like a single thread's row so it draws the
 *                    same way. For a group of one it <em>is</em> that thread's row, identity included
 */
public record ThreadGroup(String key, int threadCount, ThreadRow lane) {

    public ThreadGroup {
        if (threadCount < 1) {
            throw new IllegalArgumentException("A group needs at least one thread: " + threadCount);
        }
    }

    /**
     * Whether the lane stands for several threads. A lane of one is an ordinary thread and keeps
     * everything a thread has — its ids, and the actions that need them.
     */
    public boolean isCollapsed() {
        return threadCount > 1;
    }
}
