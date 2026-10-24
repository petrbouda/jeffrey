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

package cafe.jeffrey.profile.perfcounters;

import cafe.jeffrey.profile.perfcounters.PerfCountersOverview.Classes;
import cafe.jeffrey.profile.perfcounters.PerfCountersOverview.Collector;
import cafe.jeffrey.profile.perfcounters.PerfCountersOverview.Gc;
import cafe.jeffrey.profile.perfcounters.PerfCountersOverview.Generation;
import cafe.jeffrey.profile.perfcounters.PerfCountersOverview.Jit;
import cafe.jeffrey.profile.perfcounters.PerfCountersOverview.Jvm;
import cafe.jeffrey.profile.perfcounters.PerfCountersOverview.JvmRuntime;
import cafe.jeffrey.profile.perfcounters.PerfCountersOverview.Memory;
import cafe.jeffrey.profile.perfcounters.PerfCountersOverview.Pool;
import cafe.jeffrey.profile.perfcounters.PerfCountersOverview.Safepoints;
import cafe.jeffrey.profile.perfcounters.PerfCountersOverview.Threads;
import cafe.jeffrey.profile.perfcounters.PerfCountersOverview.UptimeBasis;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Picks the headline figures out of a perf-data snapshot. Generations, their spaces and the collectors
 * are found by the counters the JVM wrote rather than by its policy counters: G1 reports one collector
 * in {@code sun.gc.policy.collectors} and writes three.
 */
public final class PerfCountersOverviewBuilder {

    private static final String VM_NAME = "java.property.java.vm.name";
    private static final String VM_VERSION = "java.property.java.vm.version";
    private static final String VM_VENDOR = "java.property.java.vm.vendor";
    private static final String JAVA_VERSION = "java.property.java.version";
    private static final String JAVA_COMMAND = "sun.rt.javaCommand";
    private static final String VM_ARGS = "java.rt.vmArgs";
    private static final String GC_POLICY = "sun.gc.policy.name";

    private static final String CREATE_VM_BEGIN = "sun.rt.createVmBeginTime";
    private static final String CREATE_VM_END = "sun.rt.createVmEndTime";
    private static final String TIMER_TICKS = "sun.os.hrt.ticks";
    private static final String APPLICATION_TIME = "sun.rt.applicationTime";

    private static final String SAFEPOINTS = "sun.rt.safepoints";
    private static final String SAFEPOINT_TIME = "sun.rt.safepointTime";
    private static final String SAFEPOINT_SYNC_TIME = "sun.rt.safepointSyncTime";

    private static final Pattern GENERATION_NAME = Pattern.compile("sun\\.gc\\.generation\\.(\\d+)\\.name");
    private static final String GENERATION = "sun.gc.generation.%d.";
    private static final String SPACE_NAME = "space\\.(\\d+)\\.name";
    private static final String SPACE_USED = "space.%d.used";
    private static final String NAME = "name";
    private static final String USED = "used";
    private static final String CAPACITY = "capacity";
    private static final String MAX_CAPACITY = "maxCapacity";
    private static final String METASPACE = "sun.gc.metaspace.";
    private static final String COMPRESSED_CLASS_SPACE = "sun.gc.compressedclassspace.";

    private static final Pattern COLLECTOR_NAME = Pattern.compile("sun\\.gc\\.collector\\.(\\d+)\\.name");
    private static final String COLLECTOR = "sun.gc.collector.%d.";
    private static final String INVOCATIONS = "invocations";
    private static final String TIME = "time";
    private static final String GC_LAST_CAUSE = "sun.gc.lastCause";

    private static final String CLASSES_LOADED = "java.cls.loadedClasses";
    private static final String CLASSES_UNLOADED = "java.cls.unloadedClasses";
    private static final String CLASSES_SHARED_LOADED = "java.cls.sharedLoadedClasses";
    private static final String CLASSES_LOADED_BYTES = "sun.cls.loadedBytes";
    private static final String CLASSES_TIME = "sun.cls.time";

    private static final String JIT_COMPILES = "sun.ci.totalCompiles";
    private static final String JIT_BAILOUTS = "sun.ci.totalBailouts";
    private static final String JIT_INVALIDATIONS = "sun.ci.totalInvalidates";
    private static final String JIT_TIME = "java.ci.totalTime";
    private static final String JIT_CODE_BYTES = "sun.ci.nmethodCodeSize";

    private static final String THREADS_LIVE = "java.threads.live";
    private static final String THREADS_PEAK = "java.threads.livePeak";
    private static final String THREADS_DAEMON = "java.threads.daemon";
    private static final String THREADS_STARTED = "java.threads.started";

    private PerfCountersOverviewBuilder() {
    }

    public static PerfCountersOverview build(PerfCounterValues values) {
        JvmRuntime runtime = runtime(values);
        return new PerfCountersOverview(
                new Jvm(
                        values.text(VM_NAME),
                        values.text(VM_VERSION),
                        values.text(VM_VENDOR),
                        values.text(JAVA_VERSION),
                        values.text(JAVA_COMMAND),
                        values.text(VM_ARGS),
                        values.text(GC_POLICY)),
                runtime,
                new Memory(generations(values), pool(values, METASPACE), pool(values, COMPRESSED_CLASS_SPACE)),
                gc(values, runtime.uptimeNanos()),
                new Classes(
                        values.number(CLASSES_LOADED),
                        values.number(CLASSES_UNLOADED),
                        values.number(CLASSES_SHARED_LOADED),
                        values.number(CLASSES_LOADED_BYTES),
                        values.number(CLASSES_TIME)),
                new Safepoints(
                        values.number(SAFEPOINTS),
                        values.number(SAFEPOINT_TIME),
                        values.number(SAFEPOINT_SYNC_TIME)),
                new Jit(
                        values.number(JIT_COMPILES),
                        values.number(JIT_BAILOUTS),
                        values.number(JIT_INVALIDATIONS),
                        values.number(JIT_TIME),
                        values.number(JIT_CODE_BYTES)),
                new Threads(
                        values.number(THREADS_LIVE),
                        values.number(THREADS_PEAK),
                        values.number(THREADS_DAEMON),
                        values.number(THREADS_STARTED)));
    }

    /**
     * The JVM's own timer when it wrote one; otherwise application time plus safepoint time, which
     * between them cover the run up to its last safepoint -- the whole run for a snapshot taken at exit.
     */
    private static JvmRuntime runtime(PerfCounterValues values) {
        Long begin = values.number(CREATE_VM_BEGIN);
        Long end = values.number(CREATE_VM_END);
        Long vmInit = begin != null && end != null && end >= begin ? end - begin : null;
        Long applicationTime = values.number(APPLICATION_TIME);
        Long safepointTime = values.number(SAFEPOINT_TIME);

        Long timer = values.number(TIMER_TICKS);
        if (timer != null && timer > 0) {
            return new JvmRuntime(begin, vmInit, timer, UptimeBasis.TIMER, applicationTime);
        }
        if (applicationTime != null && safepointTime != null) {
            return new JvmRuntime(begin, vmInit, applicationTime + safepointTime, UptimeBasis.SAFEPOINTS,
                    applicationTime);
        }
        return new JvmRuntime(begin, vmInit, null, null, applicationTime);
    }

    private static List<Generation> generations(PerfCounterValues values) {
        return values.indexes(GENERATION_NAME).stream()
                .map(index -> {
                    String prefix = GENERATION.formatted(index);
                    Pattern spaceName = Pattern.compile(Pattern.quote(prefix) + SPACE_NAME);
                    List<Long> spacesUsed = values.indexes(spaceName).stream()
                            .map(space -> values.number(prefix + SPACE_USED.formatted(space)))
                            .filter(Objects::nonNull)
                            .toList();
                    return new Generation(
                            values.text(prefix + NAME),
                            spacesUsed.isEmpty() ? null : spacesUsed.stream().mapToLong(Long::longValue).sum(),
                            values.number(prefix + CAPACITY),
                            values.number(prefix + MAX_CAPACITY));
                })
                .toList();
    }

    private static Pool pool(PerfCounterValues values, String prefix) {
        return new Pool(
                values.number(prefix + USED),
                values.number(prefix + CAPACITY),
                values.number(prefix + MAX_CAPACITY));
    }

    private static Gc gc(PerfCounterValues values, Long uptimeNanos) {
        List<Collector> collectors = values.indexes(COLLECTOR_NAME).stream()
                .map(index -> {
                    String prefix = COLLECTOR.formatted(index);
                    return new Collector(
                            values.text(prefix + NAME),
                            values.number(prefix + INVOCATIONS),
                            values.number(prefix + TIME));
                })
                .toList();

        Long invocations = sum(collectors, Collector::invocations);
        Long time = sum(collectors, Collector::timeNanos);
        Double share = time != null && uptimeNanos != null && uptimeNanos > 0
                ? (double) time / uptimeNanos
                : null;
        return new Gc(collectors, invocations, time, share, values.text(GC_LAST_CAUSE));
    }

    /** The sum of the collectors' figures, or null when none of them wrote one. */
    private static Long sum(List<Collector> collectors, Function<Collector, Long> figure) {
        List<Long> known = collectors.stream()
                .map(figure)
                .filter(Objects::nonNull)
                .toList();
        return known.isEmpty() ? null : known.stream().mapToLong(Long::longValue).sum();
    }
}
