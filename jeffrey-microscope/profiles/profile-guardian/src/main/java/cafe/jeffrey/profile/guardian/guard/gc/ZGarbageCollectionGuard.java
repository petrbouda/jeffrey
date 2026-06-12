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

package cafe.jeffrey.profile.guardian.guard.gc;

import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.profile.guardian.guard.TraversableGuard;
import cafe.jeffrey.profile.guardian.matcher.FrameMatchers;
import cafe.jeffrey.profile.guardian.preconditions.Preconditions;
import cafe.jeffrey.profile.guardian.traverse.*;

import java.util.List;
import java.util.function.Supplier;

/**
 * Guards GC overhead of ZGC. One class covers both the single-generation and the generational
 * variant — the guard name, VM operation frame prefix and the matched {@link GarbageCollectorType}
 * live in {@link Variant}.
 */
public class ZGarbageCollectionGuard extends TraversableGuard {

    public enum Variant {
        Z("Z GC", "VM_XOperation", GarbageCollectorType.Z),
        Z_GENERATIONAL("Z Generational GC", "VM_ZOperation", GarbageCollectorType.ZGENERATIONAL);

        private final String guardName;
        private final String vmOperationPrefix;
        private final GarbageCollectorType collectorType;

        Variant(String guardName, String vmOperationPrefix, GarbageCollectorType collectorType) {
            this.guardName = guardName;
            this.vmOperationPrefix = vmOperationPrefix;
            this.collectorType = collectorType;
        }
    }

    private final Variant variant;

    public ZGarbageCollectionGuard(Variant variant, ProfileInfo profileInfo, double infoThreshold, double warningThreshold) {
        super(variant.guardName,
                profileInfo,
                infoThreshold,
                warningThreshold,
                FrameMatchers.jvm("Thread::call_run"),
                Category.GARBAGE_COLLECTION,
                createTraversables(variant),
                TargetFrameType.JVM,
                MatchingType.SINGLE_MATCH,
                ResultType.SAMPLES);

        this.variant = variant;
    }

    private static Supplier<List<Traversable>> createTraversables(Variant variant) {
        return () -> List.of(
                new NameBasedSingleTraverser("ConcurrentGCThread::run"),
                new NameBasedSingleTraverser("WorkerThread::run"),
                new BaseWithMatcherTraverser(
                        FrameMatchers.jvm("VM_Operation::evaluate"),
                        FrameMatchers.prefix(variant.vmOperationPrefix))
        );
    }

    @Override
    public Preconditions preconditions() {
        return Preconditions.builder()
                .withGarbageCollectorType(variant.collectorType)
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
