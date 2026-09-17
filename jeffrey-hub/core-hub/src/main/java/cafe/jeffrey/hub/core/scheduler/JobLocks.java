/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
package cafe.jeffrey.hub.core.scheduler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * One lock per job, so that a job never runs concurrently with itself whichever way it was
 * started: the scheduler's tick and an operator's manual run take the same lock. Shared by the
 * two rather than owned by the scheduler, because the manual run is the request thread's and
 * must not become a scheduled task to be serialized.
 */
public class JobLocks {

    private final ConcurrentMap<Job, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T exclusively(Job job, Supplier<T> body) {
        ReentrantLock lock = locks.computeIfAbsent(job, _ -> new ReentrantLock());
        lock.lock();
        try {
            return body.get();
        } finally {
            lock.unlock();
        }
    }
}
