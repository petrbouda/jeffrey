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
