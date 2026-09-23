/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
