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

package cafe.jeffrey.profile.trace.export;

import cafe.jeffrey.microscope.model.Type;

import java.util.Map;
import java.util.Objects;

/**
 * How much of a trace's own work its threads were seen running on a CPU: the on-CPU samples taken on
 * the trace's threads inside its windows.
 * <p>
 * The trace's {@code OWN_WORK} is a residual — whatever no pause or wait accounts for — and a thread
 * that is not running at all leaves the same residual as one computing: descheduled behind other
 * threads, or held by the container's CPU quota. The samples tell the two apart. A residual the
 * samples cover was code running; one they do not was the thread off the CPU with nothing recorded to
 * say why.
 */
public sealed interface TraceOnCpu {

    /**
     * The on-CPU samples counted.
     *
     * @param eventType     the on-CPU sample type counted ({@code jdk.ExecutionSample}, {@code jdk.CPUTimeSample})
     * @param samples       samples taken on the trace's threads inside its windows
     * @param threads       how many threads the trace's windows were on; past one, the samples are summed
     *                      over threads running side by side and can add up to more than the trace's window
     * @param onCpuNanos    the time those samples stand for, or {@code null} when the profile does not say
     *                      how often it sampled
     * @param intervalNanos how much running one sample stands for — the resolution of {@code onCpuNanos},
     *                      which is good to about one interval either way — or {@code null} when unknown
     */
    record Measured(String eventType, long samples, int threads, Long onCpuNanos, Long intervalNanos)
            implements TraceOnCpu {

        /** Where the async-profiler and JDK samplers put the period they sampled at, in nanoseconds. */
        private static final String SAMPLE_INTERVAL_EXTRA = "sample_interval";

        public Measured {
            Objects.requireNonNull(eventType, "eventType");
            if (samples < 0) {
                throw new IllegalArgumentException("samples must not be negative: " + samples);
            }
            if (threads < 1) {
                throw new IllegalArgumentException("a trace runs on at least one thread: " + threads);
            }
            if (onCpuNanos != null && onCpuNanos < 0) {
                throw new IllegalArgumentException("onCpuNanos must not be negative: " + onCpuNanos);
            }
            if (intervalNanos != null && intervalNanos <= 0) {
                throw new IllegalArgumentException("intervalNanos must be positive: " + intervalNanos);
            }
        }

        /**
         * The samples of one type inside the trace's windows, with the time they stand for: a CPU-time
         * sample carries its own period as its weight; an execution sample stands for the interval the
         * profile sampled at.
         *
         * @param weight the samples' summed weight, as the event's weight extractor stored it
         * @param extras the event type's extras, where the sampling interval is kept
         */
        public static Measured of(String eventType, long samples, long weight, Map<String, String> extras, int threads) {
            if (Type.CPU_TIME_SAMPLE.sameAs(eventType)) {
                if (samples == 0) {
                    return new Measured(eventType, 0, threads, 0L, null);
                }
                return weight > 0
                        ? new Measured(eventType, samples, threads, weight, Math.max(1, weight / samples))
                        : new Measured(eventType, samples, threads, null, null);
            }
            Long interval = intervalOf(extras);
            if (interval == null) {
                return new Measured(eventType, samples, threads, samples == 0 ? 0L : null, null);
            }
            try {
                return new Measured(eventType, samples, threads, Math.multiplyExact(samples, interval), interval);
            } catch (ArithmeticException _) {
                return new Measured(eventType, samples, threads, null, interval);
            }
        }

        private static Long intervalOf(Map<String, String> extras) {
            String interval = extras == null ? null : extras.get(SAMPLE_INTERVAL_EXTRA);
            if (interval == null) {
                return null;
            }
            try {
                long nanos = Long.parseLong(interval);
                return nanos > 0 ? nanos : null;
            } catch (NumberFormatException _) {
                return null;
            }
        }
    }

    /**
     * Nothing could be counted, and why: the profile recorded no on-CPU samples, or the trace never ran
     * on a platform thread (the profiler files a virtual thread's samples under its carrier).
     */
    record NotMeasured(String reason) implements TraceOnCpu {

        public NotMeasured {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
