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

package cafe.jeffrey.profile.guardian.guard;

import cafe.jeffrey.profile.guardian.GuardianProperties;
import cafe.jeffrey.profile.guardian.guard.alloc.*;
import cafe.jeffrey.profile.guardian.guard.app.*;
import cafe.jeffrey.profile.guardian.guard.blocking.*;
import cafe.jeffrey.profile.guardian.guard.gc.*;
import cafe.jeffrey.profile.guardian.guard.jit.JITCompilationGuard;
import cafe.jeffrey.profile.guardian.guard.jvm.DeoptimizationGuard;
import cafe.jeffrey.profile.guardian.guard.jvm.SafepointOverheadGuard;
import cafe.jeffrey.profile.guardian.guard.jvm.VMOperationOverheadGuard;
import cafe.jeffrey.profile.guardian.traverse.ResultType;

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
            new LoggingOverheadGuard(LoggingOverheadGuard.Framework.LOGBACK, "Logback CPU Overhead", ResultType.SAMPLES, pi, p.logbackInfoThreshold(), p.logbackWarningThreshold())),
    LOG4J_CPU(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new LoggingOverheadGuard(LoggingOverheadGuard.Framework.LOG4J, "Log4j CPU Overhead", ResultType.SAMPLES, pi, p.log4jInfoThreshold(), p.log4jWarningThreshold())),
    HASH_MAP_COLLISION(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new HashMapCollisionGuard(pi, p.hashMapCollisionInfoThreshold(), p.hashMapCollisionWarningThreshold())),
    REGEX(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new RegexOverheadGuard(pi, p.regexInfoThreshold(), p.regexWarningThreshold())),
    CLASS_LOADING(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new ClassLoadingOverheadGuard(pi, p.classLoadingInfoThreshold(), p.classLoadingWarningThreshold())),
    REFLECTION(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new ReflectionOverheadGuard(pi, p.reflectionInfoThreshold(), p.reflectionWarningThreshold())),
    SERIALIZATION(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new SerializationOverheadGuard(pi, p.serializationInfoThreshold(), p.serializationWarningThreshold())),
    XML_PARSING(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new XMLParsingOverheadGuard(pi, p.xmlParsingInfoThreshold(), p.xmlParsingWarningThreshold())),
    JSON_PROCESSING(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new JSONProcessingOverheadGuard(pi, p.jsonProcessingInfoThreshold(), p.jsonProcessingWarningThreshold())),
    EXCEPTION(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new ExceptionOverheadGuard(pi, p.exceptionInfoThreshold(), p.exceptionWarningThreshold())),
    STRING_CONCAT(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new StringConcatOverheadGuard(pi, p.stringConcatInfoThreshold(), p.stringConcatWarningThreshold())),
    THREAD_SYNCHRONIZATION(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new ThreadSynchronizationOverheadGuard(pi, p.threadSyncInfoThreshold(), p.threadSyncWarningThreshold())),
    CRYPTO(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new CryptoOverheadGuard(pi, p.cryptoInfoThreshold(), p.cryptoWarningThreshold())),
    COMPRESS(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new CompressOverheadGuard(pi, p.compressInfoThreshold(), p.compressWarningThreshold())),
    FINALIZER_CLEANER(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new FinalizerCleanerOverheadGuard(pi, p.finalizerCleanerInfoThreshold(), p.finalizerCleanerWarningThreshold())),
    JIT_COMPILATION(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new JITCompilationGuard(pi, p.jitCompilationInfoThreshold(), p.jitCompilationWarningThreshold())),
    DEOPTIMIZATION(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new DeoptimizationGuard(pi, p.deoptimizationInfoThreshold(), p.deoptimizationWarningThreshold())),
    SAFEPOINT(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new SafepointOverheadGuard(pi, p.safepointInfoThreshold(), p.safepointWarningThreshold())),
    VM_OPERATION(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new VMOperationOverheadGuard(pi, p.vmOperationInfoThreshold(), p.vmOperationWarningThreshold())),
    GC_SERIAL(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new SerialGarbageCollectionGuard(pi, p.gcInfoThreshold(), p.gcWarningThreshold())),
    GC_PARALLEL(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new ParallelGarbageCollectionGuard(pi, p.gcInfoThreshold(), p.gcWarningThreshold())),
    GC_G1(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new G1GarbageCollectionGuard(pi, p.gcInfoThreshold(), p.gcWarningThreshold())),
    GC_SHENANDOAH(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new ShenandoahGarbageCollectionGuard(pi, p.gcInfoThreshold(), p.gcWarningThreshold())),
    GC_Z(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new ZGarbageCollectionGuard(ZGarbageCollectionGuard.Variant.Z, pi, p.gcInfoThreshold(), p.gcWarningThreshold())),
    GC_Z_GENERATIONAL(GroupKind.EXECUTION_SAMPLE, (pi, p) ->
            new ZGarbageCollectionGuard(ZGarbageCollectionGuard.Variant.Z_GENERATIONAL, pi, p.gcInfoThreshold(), p.gcWarningThreshold())),

    // ===== Allocation group (allocation bytes) ===============================================

    LOGBACK_ALLOC(GroupKind.ALLOCATION, (pi, p) ->
            new LoggingOverheadGuard(LoggingOverheadGuard.Framework.LOGBACK, "Logback Allocation Overhead", ResultType.WEIGHT, pi, p.logbackAllocInfoThreshold(), p.logbackAllocWarningThreshold())),
    HASH_MAP_COLLISION_ALLOC(GroupKind.ALLOCATION, (pi, p) ->
            new HashMapCollisionAllocGuard(pi, p.hashMapCollisionAllocInfoThreshold(), p.hashMapCollisionAllocWarningThreshold())),
    REGEX_ALLOC(GroupKind.ALLOCATION, (pi, p) ->
            new RegexAllocGuard(pi, p.regexAllocInfoThreshold(), p.regexAllocWarningThreshold())),
    STRING_CONCAT_ALLOC(GroupKind.ALLOCATION, (pi, p) ->
            new StringConcatAllocGuard(pi, p.stringConcatAllocInfoThreshold(), p.stringConcatAllocWarningThreshold())),
    EXCEPTION_ALLOC(GroupKind.ALLOCATION, (pi, p) ->
            new ExceptionAllocGuard(pi, p.exceptionAllocInfoThreshold(), p.exceptionAllocWarningThreshold())),
    BOXING_ALLOC(GroupKind.ALLOCATION, (pi, p) ->
            new BoxingAllocGuard(pi, p.boxingAllocInfoThreshold(), p.boxingAllocWarningThreshold())),
    COLLECTION_ALLOC(GroupKind.ALLOCATION, (pi, p) ->
            new CollectionAllocGuard(pi, p.collectionAllocInfoThreshold(), p.collectionAllocWarningThreshold())),
    LOG4J_ALLOC(GroupKind.ALLOCATION, (pi, p) ->
            new Log4jAllocGuard(pi, p.log4jAllocInfoThreshold(), p.log4jAllocWarningThreshold())),

    // ===== Wall-Clock group ==================================================================

    LOGBACK_WALL(GroupKind.WALL_CLOCK, (pi, p) ->
            new LoggingOverheadGuard(LoggingOverheadGuard.Framework.LOGBACK, "Logback Wall-Clock Overhead", ResultType.SAMPLES, pi, p.logbackInfoThreshold(), p.logbackWarningThreshold())),
    LOG4J_WALL(GroupKind.WALL_CLOCK, (pi, p) ->
            new LoggingOverheadGuard(LoggingOverheadGuard.Framework.LOG4J, "Log4j Wall-Clock Overhead", ResultType.SAMPLES, pi, p.log4jInfoThreshold(), p.log4jWarningThreshold())),
    HASH_MAP_COLLISION_WALL(GroupKind.WALL_CLOCK, (pi, p) ->
            new HashMapCollisionGuard(pi, p.hashMapCollisionInfoThreshold(), p.hashMapCollisionWarningThreshold())),
    REGEX_WALL(GroupKind.WALL_CLOCK, (pi, p) ->
            new RegexOverheadGuard(pi, p.regexInfoThreshold(), p.regexWarningThreshold())),
    REFLECTION_WALL(GroupKind.WALL_CLOCK, (pi, p) ->
            new ReflectionOverheadGuard("Reflection Wall-Clock Overhead", ResultType.SELF_SAMPLES, pi, p.reflectionInfoThreshold(), p.reflectionWarningThreshold())),
    EXCEPTION_WALL(GroupKind.WALL_CLOCK, (pi, p) ->
            new ExceptionOverheadGuard("Exception Wall-Clock Overhead", ResultType.SAMPLES, pi, p.exceptionInfoThreshold(), p.exceptionWarningThreshold())),
    CRYPTO_WALL(GroupKind.WALL_CLOCK, (pi, p) ->
            new CryptoOverheadGuard("Crypto/TLS Wall-Clock Overhead", ResultType.SAMPLES, pi, p.cryptoInfoThreshold(), p.cryptoWarningThreshold())),
    CLASS_LOADING_WALL(GroupKind.WALL_CLOCK, (pi, p) ->
            new ClassLoadingOverheadGuard("Class Loading Wall-Clock Overhead", ResultType.SELF_SAMPLES, pi, p.classLoadingInfoThreshold(), p.classLoadingWarningThreshold())),
    THREAD_SYNC_WALL(GroupKind.WALL_CLOCK, (pi, p) ->
            new ThreadSynchronizationOverheadGuard("Thread Synchronization Wall-Clock Overhead", ResultType.SAMPLES, pi, p.threadSyncInfoThreshold(), p.threadSyncWarningThreshold())),

    // ===== Blocking group (weight = duration) ================================================

    DB_POOL_BLOCKING(GroupKind.BLOCKING, (pi, p) ->
            new DatabaseConnectionPoolBlockingGuard(pi, p.dbPoolBlockingInfoThreshold(), p.dbPoolBlockingWarningThreshold())),
    LOCK_CONTENTION(GroupKind.BLOCKING, (pi, p) ->
            new LockContentionBlockingGuard(pi, p.lockContentionInfoThreshold(), p.lockContentionWarningThreshold())),
    IO_BLOCKING(GroupKind.BLOCKING, (pi, p) ->
            new IOBlockingGuard(pi, p.ioBlockingInfoThreshold(), p.ioBlockingWarningThreshold())),
    HTTP_CLIENT_BLOCKING(GroupKind.BLOCKING, (pi, p) ->
            new HttpClientBlockingGuard(pi, p.httpClientBlockingInfoThreshold(), p.httpClientBlockingWarningThreshold())),
    LOGBACK_BLOCKING(GroupKind.BLOCKING, (pi, p) ->
            new LoggingBlockingGuard(LoggingBlockingGuard.Framework.LOGBACK, pi, p.logbackBlockingInfoThreshold(), p.logbackBlockingWarningThreshold())),
    LOG4J_BLOCKING(GroupKind.BLOCKING, (pi, p) ->
            new LoggingBlockingGuard(LoggingBlockingGuard.Framework.LOG4J, pi, p.log4jBlockingInfoThreshold(), p.log4jBlockingWarningThreshold()));

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
