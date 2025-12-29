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

package pbouda.jeffrey.provider.writer.sql.enhancer;

import pbouda.jeffrey.shared.model.RecordingEventSource;
import pbouda.jeffrey.shared.model.Type;
import pbouda.jeffrey.provider.api.model.EventTypeBuilder;

/**
 * A parameterized enhancer that sets a fixed {@link RecordingEventSource} for a specific event type.
 * Replaces individual enhancer classes like MonitorWaitExtraEnhancer, NativeMallocAllocationSamplesExtraEnhancer,
 * and WallClockSamplesExtraEnhancer.
 */
public class FixedSourceEnhancer implements EventTypeEnhancer {

    private final Type eventType;
    private final RecordingEventSource source;

    public FixedSourceEnhancer(Type eventType, RecordingEventSource source) {
        this.eventType = eventType;
        this.source = source;
    }

    @Override
    public boolean isApplicable(Type type) {
        return eventType.sameAs(type);
    }

    @Override
    public EventTypeBuilder apply(EventTypeBuilder event) {
        return event.withSource(source);
    }
}
