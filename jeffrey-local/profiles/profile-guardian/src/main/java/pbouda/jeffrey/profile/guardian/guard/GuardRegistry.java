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

package pbouda.jeffrey.profile.guardian.guard;

import pbouda.jeffrey.profile.guardian.GuardianProperties;
import pbouda.jeffrey.profile.guardian.guard.alloc.*;
import pbouda.jeffrey.profile.guardian.guard.app.*;
import pbouda.jeffrey.profile.guardian.guard.blocking.*;
import pbouda.jeffrey.profile.guardian.guard.gc.*;
import pbouda.jeffrey.profile.guardian.guard.jit.JITCompilationGuard;
import pbouda.jeffrey.profile.guardian.guard.jvm.DeoptimizationGuard;
import pbouda.jeffrey.profile.guardian.guard.jvm.SafepointOverheadGuard;
import pbouda.jeffrey.profile.guardian.guard.jvm.VMOperationOverheadGuard;
import pbouda.jeffrey.profile.guardian.traverse.ResultType;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Single-source-of-truth registry of every frame-tree-traversing Guard shipped with Jeffrey.
 * <p>
 * Each entry pairs a {@link GroupKind} with a factory that takes the per-profile info and the
 * effective {@link GuardianProperties} and returns a constructed {@link Guard}. Adding a new
 * guard is a one-line enum entry plus its guard class — no more four-way edits across the group
 * factories.
 * <p>
 * Guards are filtered to the groups they belong to via {@link #forGroup}. Preconditions (event
 * source, GC type, debug symbols) remain the responsibility of each guard's
 * {@link Guard#preconditions()} as before.
 */
public enum GuardRegistry {

    // ===== Execution Sample group (CPU samples) ==============================================

    LOGBACK_CPU(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new LogbackOverheadGuard("Logback CPU Overhead", ResultType.SAMPLES, pi, p.logbackWarningThreshold())),
    LOG4J_CPU(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new Log4jOverheadGuard("Log4j CPU Overhead", ResultType.SAMPLES, pi, p.log4jWarningThreshold())),
    HASH_MAP_COLLISION(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new HashMapCollisionGuard(pi, p.hashMapCollisionWarningThreshold())),
    REGEX(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new RegexOverheadGuard(pi, p.regexWarningThreshold())),
    CLASS_LOADING(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new ClassLoadingOverheadGuard(pi, p.classLoadingWarningThreshold())),
    REFLECTION(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new ReflectionOverheadGuard(pi, p.reflectionWarningThreshold())),
    SERIALIZATION(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new SerializationOverheadGuard(pi, p.serializationWarningThreshold())),
    XML_PARSING(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new XMLParsingOverheadGuard(pi, p.xmlParsingWarningThreshold())),
    JSON_PROCESSING(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new JSONProcessingOverheadGuard(pi, p.jsonProcessingWarningThreshold())),
    EXCEPTION(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new ExceptionOverheadGuard(pi, p.exceptionWarningThreshold())),
    STRING_CONCAT(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new StringConcatOverheadGuard(pi, p.stringConcatWarningThreshold())),
    THREAD_SYNCHRONIZATION(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new ThreadSynchronizationOverheadGuard(pi, p.threadSyncWarningThreshold())),
    CRYPTO(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new CryptoOverheadGuard(pi, p.cryptoWarningThreshold())),
    COMPRESS(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new CompressOverheadGuard(pi, p.compressWarningThreshold())),
    FINALIZER_CLEANER(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new FinalizerCleanerOverheadGuard(pi, p.finalizerCleanerWarningThreshold())),
    JIT_COMPILATION(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new JITCompilationGuard(pi, p.jitCompilationWarningThreshold())),
    DEOPTIMIZATION(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new DeoptimizationGuard(pi, p.deoptimizationWarningThreshold())),
    SAFEPOINT(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new SafepointOverheadGuard(pi, p.safepointWarningThreshold())),
    VM_OPERATION(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new VMOperationOverheadGuard(pi, p.vmOperationWarningThreshold())),
    GC_SERIAL(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new SerialGarbageCollectionGuard(pi, p.gcWarningThreshold())),
    GC_PARALLEL(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new ParallelGarbageCollectionGuard(pi, p.gcWarningThreshold())),
    GC_G1(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new G1GarbageCollectionGuard(pi, p.gcWarningThreshold())),
    GC_SHENANDOAH(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new ShenandoahGarbageCollectionGuard(pi, p.gcWarningThreshold())),
    GC_Z(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new ZGarbageCollectionGuard(pi, p.gcWarningThreshold())),
    GC_Z_GENERATIONAL(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new ZGenerationalGarbageCollectionGuard(pi, p.gcWarningThreshold())),

    // ===== Allocation group (allocation bytes) ===============================================

    LOGBACK_ALLOC(GroupKind.ALLOCATION, (pi, p) ->
            new LogbackOverheadGuard("Logback Allocation Overhead", ResultType.WEIGHT, pi, p.logbackAllocWarningThreshold())),
    HASH_MAP_COLLISION_ALLOC(GroupKind.ALLOCATION, (pi, p) ->
            new HashMapCollisionAllocGuard(pi, p.hashMapCollisionAllocWarningThreshold())),
    REGEX_ALLOC(GroupKind.ALLOCATION, (pi, p) ->
            new RegexAllocGuard(pi, p.regexAllocWarningThreshold())),
    STRING_CONCAT_ALLOC(GroupKind.ALLOCATION, (pi, p) ->
            new StringConcatAllocGuard(pi, p.stringConcatAllocWarningThreshold())),
    EXCEPTION_ALLOC(GroupKind.ALLOCATION, (pi, p) ->
            new ExceptionAllocGuard(pi, p.exceptionAllocWarningThreshold())),
    BOXING_ALLOC(GroupKind.ALLOCATION, (pi, p) ->
            new BoxingAllocGuard(pi, p.boxingAllocWarningThreshold())),
    COLLECTION_ALLOC(GroupKind.ALLOCATION, (pi, p) ->
            new CollectionAllocGuard(pi, p.collectionAllocWarningThreshold())),
    LOG4J_ALLOC(GroupKind.ALLOCATION, (pi, p) ->
            new Log4jAllocGuard(pi, p.log4jAllocWarningThreshold())),

    // ===== Wall-Clock group ==================================================================

    LOGBACK_WALL(GroupKind.WALL_CLOCK, (pi, p) ->
            new LogbackOverheadGuard("Logback Wall-Clock Overhead", ResultType.SAMPLES, pi, p.logbackWarningThreshold())),
    LOG4J_WALL(GroupKind.WALL_CLOCK, (pi, p) ->
            new Log4jOverheadGuard("Log4j Wall-Clock Overhead", ResultType.SAMPLES, pi, p.log4jWarningThreshold())),
    HASH_MAP_COLLISION_WALL(GroupKind.WALL_CLOCK, (pi, p) ->
            new HashMapCollisionGuard(pi, p.hashMapCollisionWarningThreshold())),
    REGEX_WALL(GroupKind.WALL_CLOCK, (pi, p) ->
            new RegexOverheadGuard(pi, p.regexWarningThreshold())),
    REFLECTION_WALL(GroupKind.WALL_CLOCK, (pi, p) ->
            new ReflectionOverheadGuard("Reflection Wall-Clock Overhead", ResultType.SELF_SAMPLES, pi, p.reflectionWarningThreshold())),
    EXCEPTION_WALL(GroupKind.WALL_CLOCK, (pi, p) ->
            new ExceptionOverheadGuard("Exception Wall-Clock Overhead", ResultType.SAMPLES, pi, p.exceptionWarningThreshold())),
    CRYPTO_WALL(GroupKind.WALL_CLOCK, (pi, p) ->
            new CryptoOverheadGuard("Crypto/TLS Wall-Clock Overhead", ResultType.SAMPLES, pi, p.cryptoWarningThreshold())),
    CLASS_LOADING_WALL(GroupKind.WALL_CLOCK, (pi, p) ->
            new ClassLoadingOverheadGuard("Class Loading Wall-Clock Overhead", ResultType.SELF_SAMPLES, pi, p.classLoadingWarningThreshold())),
    THREAD_SYNC_WALL(GroupKind.WALL_CLOCK, (pi, p) ->
            new ThreadSynchronizationOverheadGuard("Thread Synchronization Wall-Clock Overhead", ResultType.SAMPLES, pi, p.threadSyncWarningThreshold())),

    // ===== Blocking group (weight = duration) ================================================

    DB_POOL_BLOCKING(GroupKind.BLOCKING, (pi, p) ->
            new DatabaseConnectionPoolBlockingGuard(pi, p.dbPoolBlockingWarningThreshold())),
    LOCK_CONTENTION(GroupKind.BLOCKING, (pi, p) ->
            new LockContentionBlockingGuard(pi, p.lockContentionWarningThreshold())),
    IO_BLOCKING(GroupKind.BLOCKING, (pi, p) ->
            new IOBlockingGuard(pi, p.ioBlockingWarningThreshold())),
    HTTP_CLIENT_BLOCKING(GroupKind.BLOCKING, (pi, p) ->
            new HttpClientBlockingGuard(pi, p.httpClientBlockingWarningThreshold())),
    LOGBACK_BLOCKING(GroupKind.BLOCKING, (pi, p) ->
            new LogbackBlockingGuard(pi, p.logbackBlockingWarningThreshold())),
    LOG4J_BLOCKING(GroupKind.BLOCKING, (pi, p) ->
            new Log4jBlockingGuard(pi, p.log4jBlockingWarningThreshold()));

    private final GroupKind group;
    private final BiFunction<Guard.ProfileInfo, GuardianProperties, Guard> factory;

    GuardRegistry(GroupKind group, BiFunction<Guard.ProfileInfo, GuardianProperties, Guard> factory) {
        this.group = group;
        this.factory = factory;
    }

    public GroupKind group() {
        return group;
    }

    /** Instantiates every registered guard belonging to {@code kind}. */
    public static List<Guard> instantiateFor(GroupKind kind, Guard.ProfileInfo profileInfo, GuardianProperties props) {
        return Arrays.stream(values())
                .filter(entry -> entry.group == kind)
                .map(entry -> entry.factory.apply(profileInfo, props))
                .toList();
    }
}
