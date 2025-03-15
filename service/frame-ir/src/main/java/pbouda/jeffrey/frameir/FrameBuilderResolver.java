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

package pbouda.jeffrey.frameir;

import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.frameir.frame.AllocationTopFrameProcessor;
import pbouda.jeffrey.frameir.frame.BlockingTopFrameProcessor;

public class FrameBuilderResolver {

    private final Type type;
    private final boolean threadMode;
    private final boolean parseLocations;
    private final boolean differentialMode;

    public FrameBuilderResolver(GraphParameters params, boolean differentialMode) {
        this.type = params.eventType();
        this.threadMode = params.threadMode();
        this.parseLocations = params.parseLocations();
        this.differentialMode = differentialMode;
    }

    public FrameBuilder resolve() {
        // Handling/Fixing lambdas is only supported in differential mode
        boolean handleLambdas = differentialMode;

        if (type.isAllocationEvent()) {
            return new FrameBuilder(handleLambdas, threadMode, parseLocations, new AllocationTopFrameProcessor());
        } else if (type.isBlockingEvent()) {
            return new FrameBuilder(handleLambdas, threadMode, parseLocations, new BlockingTopFrameProcessor());
        } else {
            return new FrameBuilder(handleLambdas, threadMode, parseLocations, null);
        }
    }
}
