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

package pbouda.jeffrey.profile.guardian.guard.gc;

import pbouda.jeffrey.common.model.RecordingEventSource;
import pbouda.jeffrey.profile.common.event.GarbageCollectorType;
import pbouda.jeffrey.profile.guardian.guard.Guard;
import pbouda.jeffrey.profile.guardian.guard.TraversableGuard;
import pbouda.jeffrey.profile.guardian.matcher.FrameMatchers;
import pbouda.jeffrey.profile.guardian.preconditions.Preconditions;
import pbouda.jeffrey.profile.guardian.traverse.*;

import java.util.List;
import java.util.function.Supplier;

public class ZGenerationalGarbageCollectionGuard extends TraversableGuard {

    public ZGenerationalGarbageCollectionGuard(Guard.ProfileInfo profileInfo, double threshold) {
        super("Z Generational GC",
                profileInfo,
                threshold,
                FrameMatchers.jvm("Thread::call_run"),
                Category.GARBAGE_COLLECTION,
                createTraversables(),
                TargetFrameType.JVM,
                MatchingType.SINGLE_MATCH,
                ResultType.SAMPLES);
    }

    private static Supplier<List<Traversable>> createTraversables() {
        return () -> List.of(
                new NameBasedSingleTraverser("ConcurrentGCThread::run"),
                new NameBasedSingleTraverser("WorkerThread::run"),
                new BaseWithMatcherTraverser(
                        FrameMatchers.jvm("VM_Operation::evaluate"),
                        FrameMatchers.prefix("VM_ZOperation"))
        );
    }

    @Override
    public Preconditions preconditions() {
        return Preconditions.builder()
                .withGarbageCollectorType(GarbageCollectorType.ZGENERATIONAL)
                .withEventSource(RecordingEventSource.ASYNC_PROFILER)
                .build();
    }

    @Override
    protected String summary() {
        return "";
    }

    @Override
    protected String explanation() {
        return "";
    }

    @Override
    protected String solution() {
        return "";
    }
}
