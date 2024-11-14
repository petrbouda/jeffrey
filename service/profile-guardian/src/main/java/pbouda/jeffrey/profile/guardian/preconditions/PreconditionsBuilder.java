/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.profile.guardian.preconditions;

import pbouda.jeffrey.common.EventSource;
import pbouda.jeffrey.common.GarbageCollectorType;
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.profile.summary.event.EventSummary;

import java.util.List;

public class PreconditionsBuilder {

    private Boolean debugSymbolsAvailable;
    private Boolean kernelSymbolsAvailable;
    private List<EventSummary> eventTypes = List.of();
    private EventSource eventSource;
    private GarbageCollectorType garbageCollectorType;
    private GraphType graphType;

    public PreconditionsBuilder withEventTypes(List<EventSummary> eventTypes) {
        this.eventTypes = eventTypes;
        return this;
    }

    public PreconditionsBuilder withDebugSymbolsAvailable(boolean debugSymbolsAvailable) {
        this.debugSymbolsAvailable = debugSymbolsAvailable;
        return this;
    }

    public PreconditionsBuilder withKernelSymbolsAvailable(boolean kernelSymbolsAvailable) {
        this.kernelSymbolsAvailable = kernelSymbolsAvailable;
        return this;
    }

    public PreconditionsBuilder withEventSource(EventSource eventSource) {
        this.eventSource = eventSource;
        return this;
    }

    public PreconditionsBuilder withGarbageCollectorType(GarbageCollectorType garbageCollectorType) {
        this.garbageCollectorType = garbageCollectorType;
        return this;
    }

    public PreconditionsBuilder withGraphType(GraphType graphType) {
        this.graphType = graphType;
        return this;
    }

    public Preconditions build() {
        return new Preconditions(
                debugSymbolsAvailable,
                kernelSymbolsAvailable,
                eventTypes,
                eventSource,
                garbageCollectorType,
                graphType);
    }
}
