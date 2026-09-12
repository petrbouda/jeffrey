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
