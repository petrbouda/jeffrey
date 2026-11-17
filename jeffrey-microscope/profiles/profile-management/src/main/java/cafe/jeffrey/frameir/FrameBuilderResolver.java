/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.frameir;

import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.WeightUnit;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.frameir.frame.AllocationTopFrameProcessor;
import cafe.jeffrey.frameir.frame.BlockingTopFrameProcessor;
import cafe.jeffrey.frameir.frame.MethodTraceTopFrameProcessor;

public class FrameBuilderResolver {

    private final Type type;
    private final WeightUnit weightUnit;
    private final boolean threadMode;
    private final boolean parseLocations;
    private final boolean differentialMode;
    private final boolean flamegraphOnlyImport;

    public FrameBuilderResolver(GraphParameters params, boolean differentialMode) {
        this.type = params.eventType();
        this.weightUnit = params.weightUnit();
        this.threadMode = params.threadMode();
        this.parseLocations = params.parseLocations();
        this.differentialMode = differentialMode;
        this.flamegraphOnlyImport = params.flamegraphOnlyImport();
    }

    public FrameBuilder resolve() {
        // A hidden class carries the JVM's address in its name, redrawn on every run, so the same
        // lambda is a different frame in every recording. Only the differential compares two
        // recordings, so only the differential has to drop those frames.
        boolean excludeHiddenFrames = differentialMode;

        // Aggregated stack-sample formats (pprof/OTLP) select the top-frame processor by their weight unit
        // (bytes -> allocation type leaf, duration -> blocking entity leaf); both are no-ops when the
        // record carries no weight entity. JFR (unit NONE) is classified by the event-type Type — but for
        // imported profiles that event-code fallback is skipped, so a NONE-unit count stays a plain graph.
        boolean jfrClassified = !flamegraphOnlyImport;
        if (weightUnit == WeightUnit.BYTES || (jfrClassified && weightUnit == WeightUnit.NONE && type.isAllocationEvent())) {
            return new FrameBuilder(excludeHiddenFrames, threadMode, parseLocations, new AllocationTopFrameProcessor());
        } else if (weightUnit == WeightUnit.DURATION || (jfrClassified && weightUnit == WeightUnit.NONE && type.isBlockingEvent())) {
            return new FrameBuilder(excludeHiddenFrames, threadMode, parseLocations, new BlockingTopFrameProcessor());
        } else if (jfrClassified && weightUnit == WeightUnit.NONE && type.isMethodTraceEvent()) {
            // JEP 520 leaves the traced method off its own stack trace; this appends it back as the leaf.
            // JFR only: an imported profile carries no per-sample method entity to append.
            return new FrameBuilder(excludeHiddenFrames, threadMode, parseLocations, new MethodTraceTopFrameProcessor());
        } else {
            return new FrameBuilder(excludeHiddenFrames, threadMode, parseLocations, null);
        }
    }
}
