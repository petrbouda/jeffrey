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

package pbouda.jeffrey.jmh.flamegraph.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import pbouda.jeffrey.frameir.Frame;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializes {@link Frame} trees to JSON for verification purposes.
 * Converts the Frame tree structure to a nested Map that can be serialized to JSON.
 */
public class FrameJsonSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Serialize a Frame tree to JSON bytes.
     */
    public static byte[] toJsonBytes(Frame frame) {
        try {
            return MAPPER.writeValueAsBytes(toMap(frame));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize Frame to JSON", e);
        }
    }

    /**
     * Convert Frame to a Map structure for JSON serialization.
     * Includes all frame metrics and recursively processes children.
     */
    private static Map<String, Object> toMap(Frame frame) {
        Map<String, Object> map = new LinkedHashMap<>();

        // Frame identity
        map.put("methodName", frame.methodName());
        map.put("lineNumber", frame.lineNumber());
        map.put("bci", frame.bci());
        map.put("frameType", frame.frameType().name());

        // Sample counts
        map.put("totalSamples", frame.totalSamples());
        map.put("totalWeight", frame.totalWeight());
        map.put("selfSamples", frame.selfSamples());
        map.put("selfWeight", frame.selfWeight());

        // Frame type breakdown
        map.put("inlinedSamples", frame.inlinedSamples());
        map.put("c1Samples", frame.c1Samples());
        map.put("jitCompiledSamples", frame.jitCompiledSamples());
        map.put("interpretedSamples", frame.interpretedSamples());

        // Children (sorted by key for deterministic output)
        if (!frame.isEmpty()) {
            Map<String, Object> children = new LinkedHashMap<>();
            for (Map.Entry<String, Frame> entry : frame.entrySet()) {
                children.put(entry.getKey(), toMap(entry.getValue()));
            }
            map.put("children", children);
        }

        return map;
    }

    /**
     * Calculate summary statistics for a Frame tree.
     */
    public static FrameSummary summarize(Frame frame) {
        int[] counts = {0, 0}; // [frameCount, maxDepth]
        countFrames(frame, 0, counts);
        return new FrameSummary(
                counts[0],
                counts[1],
                frame.totalSamples(),
                frame.totalWeight()
        );
    }

    private static void countFrames(Frame frame, int depth, int[] counts) {
        counts[0]++; // frame count
        counts[1] = Math.max(counts[1], depth); // max depth
        for (Frame child : frame.values()) {
            countFrames(child, depth + 1, counts);
        }
    }

    public record FrameSummary(int frameCount, int maxDepth, long totalSamples, long totalWeight) {
        @Override
        public String toString() {
            return String.format("FrameSummary{frames=%d, depth=%d, samples=%d, weight=%d}",
                    frameCount, maxDepth, totalSamples, totalWeight);
        }
    }
}
