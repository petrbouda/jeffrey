/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.manager.custom.builder;

import org.HdrHistogram.Histogram;
import pbouda.jeffrey.jfrparser.api.type.JfrMethod;
import pbouda.jeffrey.profile.manager.custom.model.method.MethodTracingSlowestData;
import pbouda.jeffrey.profile.manager.custom.model.method.MethodTracingSlowestHeader;
import pbouda.jeffrey.profile.manager.custom.model.method.SlowestMethodTrace;
import pbouda.jeffrey.provider.profile.builder.RecordBuilder;
import pbouda.jeffrey.provider.profile.model.GenericRecord;

import java.util.*;

/**
 * Builder for extracting slowest method traces from individual GenericRecords.
 *
 * <p>Each GenericRecord represents a single method trace event with:
 * - method name from weightEntity (JfrMethod)
 * - actual duration from duration()
 * - thread info from thread()
 *
 * <p>This builder is optimized for the Slowest Traces page and computes:
 * - Top N slowest individual method invocations (actual slow calls, not aggregated)
 * - P99 and P95 duration percentiles using HdrHistogram
 * - Unique method count
 */
public class MethodTracingSlowestBuilder implements RecordBuilder<GenericRecord, MethodTracingSlowestData> {

    private static final int MAX_SLOW_METHODS = 100;

    // Track unique methods
    private final Set<String> uniqueMethods = new HashSet<>();

    // HdrHistogram for accurate percentile calculation
    private final Histogram durationHistogram = new Histogram(3);

    // For slowest methods tracking (min-heap by duration)
    private final PriorityQueue<SlowestMethodTrace> slowestMethods = new PriorityQueue<>(
            Comparator.comparingLong(SlowestMethodTrace::duration));

    @Override
    public void onRecord(GenericRecord record) {
        // Get method name from weightEntity (JfrMethod)
        if (!(record.weightEntity() instanceof JfrMethod method)) {
            return;
        }

        String className = method.className();
        String methodName = method.methodName();
        if (className == null) {
            return;
        }

        // Use combined key for unique method tracking
        String key = methodName != null ? className + "#" + methodName : className;

        long duration = record.sampleWeight();

        // Track unique methods
        uniqueMethods.add(key);

        // Record in histogram for accurate percentiles
        durationHistogram.recordValue(duration);

        // Track for slowest methods list
        String threadName = record.thread() != null ? record.thread().name() : "unknown";

        SlowestMethodTrace trace = new SlowestMethodTrace(
                className,
                methodName != null ? methodName : "",
                duration,
                threadName
        );

        if (slowestMethods.size() < MAX_SLOW_METHODS) {
            slowestMethods.offer(trace);
        } else {
            SlowestMethodTrace smallest = slowestMethods.peek();
            if (smallest != null && duration > smallest.duration()) {
                slowestMethods.poll();
                slowestMethods.offer(trace);
            }
        }
    }

    @Override
    public MethodTracingSlowestData build() {
        // Get accurate percentiles from HdrHistogram
        long p99Duration = durationHistogram.getTotalCount() > 0
                ? durationHistogram.getValueAtPercentile(99.0) : 0;
        long p95Duration = durationHistogram.getTotalCount() > 0
                ? durationHistogram.getValueAtPercentile(95.0) : 0;

        MethodTracingSlowestHeader header = new MethodTracingSlowestHeader(
                p99Duration,
                p95Duration,
                uniqueMethods.size()
        );

        // Convert slowest methods to list (sorted by duration descending)
        List<SlowestMethodTrace> slowestList = new ArrayList<>(slowestMethods);
        slowestList.sort(Comparator.comparingLong(SlowestMethodTrace::duration).reversed());

        return new MethodTracingSlowestData(header, slowestList);
    }
}
