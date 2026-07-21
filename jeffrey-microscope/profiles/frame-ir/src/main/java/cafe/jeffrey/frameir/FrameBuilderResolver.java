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

package cafe.jeffrey.frameir;

import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.WeightUnit;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.frameir.frame.AllocationTopFrameProcessor;
import cafe.jeffrey.frameir.frame.BlockingTopFrameProcessor;

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
        // Handling/Fixing lambdas is only supported in differential mode
        boolean handleLambdas = differentialMode;

        // Aggregated stack-sample formats (pprof/OTLP) select the top-frame processor by their weight unit
        // (bytes -> allocation type leaf, duration -> blocking entity leaf); both are no-ops when the
        // record carries no weight entity. JFR (unit NONE) is classified by the event-type Type — but for
        // imported profiles that event-code fallback is skipped, so a NONE-unit count stays a plain graph.
        boolean jfrClassified = !flamegraphOnlyImport;
        if (weightUnit == WeightUnit.BYTES || (jfrClassified && weightUnit == WeightUnit.NONE && type.isAllocationEvent())) {
            return new FrameBuilder(handleLambdas, threadMode, parseLocations, new AllocationTopFrameProcessor());
        } else if (weightUnit == WeightUnit.DURATION || (jfrClassified && weightUnit == WeightUnit.NONE && type.isBlockingEvent())) {
            return new FrameBuilder(handleLambdas, threadMode, parseLocations, new BlockingTopFrameProcessor());
        } else {
            return new FrameBuilder(handleLambdas, threadMode, parseLocations, null);
        }
    }
}
