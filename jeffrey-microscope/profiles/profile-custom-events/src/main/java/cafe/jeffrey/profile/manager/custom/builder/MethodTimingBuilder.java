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

package cafe.jeffrey.profile.manager.custom.builder;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTimingData;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTimingStat;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code jdk.MethodTiming} into one row per method by keeping the <b>latest tally</b>, never
 * by summing.
 *
 * <h2>Why this must not sum</h2>
 * The event is periodic — {@code period=endChunk} — and its counters are <b>cumulative over the
 * whole recording</b>, not per chunk. Measured on a JDK 26 probe calling one method 1000 times a
 * second: a dump at five seconds carried a single event reading {@code invocations = 5000}, and a
 * dump at ten seconds carried two events reading {@code 5000} and {@code 11000}. The second event
 * is the running total, not the second chunk's contribution.
 * <p>
 * So a recording with five chunks holds five events per method, each a superset of the last, and
 * adding them up reports roughly three times the calls that happened. Only the final tally is the
 * answer, and the same applies to every column: {@code minimum} and {@code maximum} are already the
 * extremes over everything recorded so far, and {@code average} is the mean over every call, which
 * is why averaging the averages — or trying to difference them — produces a number that means
 * nothing.
 * <p>
 * The final tally is picked by the largest {@code invocations} rather than by the latest timestamp.
 * The counter only ever grows, so the largest value <em>is</em> the newest, and reading it that way
 * does not depend on events arriving in order.
 */
public class MethodTimingBuilder implements RecordBuilder<GenericRecord, MethodTimingData> {

    private static final String METHOD_FIELD = "method";
    private static final String INVOCATIONS_FIELD = "invocations";
    private static final String MINIMUM_FIELD = "minimum";
    private static final String AVERAGE_FIELD = "average";
    private static final String MAXIMUM_FIELD = "maximum";

    /** How {@code EventFieldsToJsonMapper} flattens the JFR method struct. */
    private static final String METHOD_SEPARATOR = "#";

    private static final String UNKNOWN_CLASS = "<unknown>";

    private final Map<String, MethodTimingStat> latestByMethod = new HashMap<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        if (fields == null) {
            return;
        }
        String method = Json.readString(fields, METHOD_FIELD);
        if (method == null || method.isBlank()) {
            return;
        }

        long invocations = Json.readLong(fields, INVOCATIONS_FIELD);
        MethodTimingStat previous = latestByMethod.get(method);
        if (previous != null && previous.invocations() >= invocations) {
            // An earlier chunk's tally, or a repeat of one already seen. The counter never shrinks,
            // so anything not larger than what is held is not newer than it.
            return;
        }

        int separator = method.lastIndexOf(METHOD_SEPARATOR);
        String className = separator > 0 ? method.substring(0, separator) : UNKNOWN_CLASS;
        String methodName = separator >= 0 ? method.substring(separator + 1) : method;

        latestByMethod.put(method, new MethodTimingStat(
                className,
                methodName,
                invocations,
                Json.readLong(fields, MINIMUM_FIELD),
                Json.readLong(fields, AVERAGE_FIELD),
                Json.readLong(fields, MAXIMUM_FIELD)));
    }

    @Override
    public MethodTimingData build() {
        if (latestByMethod.isEmpty()) {
            return MethodTimingData.EMPTY;
        }

        List<MethodTimingStat> methods = new ArrayList<>(latestByMethod.values());
        methods.sort(Comparator.comparingLong(MethodTimingStat::invocations).reversed());

        long totalInvocations = methods.stream().mapToLong(MethodTimingStat::invocations).sum();
        return new MethodTimingData(List.copyOf(methods), totalInvocations);
    }
}
