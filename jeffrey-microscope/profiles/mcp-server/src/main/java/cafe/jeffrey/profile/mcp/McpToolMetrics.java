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
package cafe.jeffrey.profile.mcp;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;

/** Aggregated endpoint measurements; arguments and result contents are never retained. */
public final class McpToolMetrics {
    private static final int MAX_TOOLS = 256;
    private long droppedCalls;
    private final Map<String, Sample> measurements = new HashMap<>();
    public record Sample(String tool, long calls, long errors, long totalDurationNanos,
                         long maxDurationNanos, long totalOutputBytes, long maxOutputBytes) {}

    public synchronized void record(String tool, long durationNanos, long outputBytes, boolean error) {
        if (!measurements.containsKey(tool) && measurements.size() >= MAX_TOOLS) {
            droppedCalls++;
            return;
        }
        Sample previous = measurements.getOrDefault(tool, new Sample(tool, 0, 0, 0, 0, 0, 0));
        measurements.put(tool, new Sample(tool, previous.calls() + 1, previous.errors() + (error ? 1 : 0),
                previous.totalDurationNanos() + durationNanos, Math.max(previous.maxDurationNanos(), durationNanos),
                previous.totalOutputBytes() + outputBytes, Math.max(previous.maxOutputBytes(), outputBytes)));
    }

    public synchronized long droppedCalls() {
        return droppedCalls;
    }

    public synchronized List<Sample> snapshot() {
        return measurements.values().stream().sorted(Comparator.comparing(Sample::tool)).toList();
    }
}
