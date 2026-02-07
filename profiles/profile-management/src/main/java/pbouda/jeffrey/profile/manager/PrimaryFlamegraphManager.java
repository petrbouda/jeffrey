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

package pbouda.jeffrey.profile.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.common.config.GraphParameters;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.flamegraph.GraphGenerator;
import pbouda.jeffrey.profile.model.EventSummaryResult;
import pbouda.jeffrey.provider.profile.repository.ProfileEventTypeRepository;

import java.time.Duration;
import java.util.List;

public class PrimaryFlamegraphManager implements FlamegraphManager {

    private static final Logger LOG = LoggerFactory.getLogger(PrimaryFlamegraphManager.class);

    private static final List<Type> SUPPORTED_EVENTS = List.of(
            Type.NATIVE_LEAK,
            Type.MALLOC,
            Type.EXECUTION_SAMPLE,
            Type.METHOD_TRACE,
            Type.WALL_CLOCK_SAMPLE,
            Type.OBJECT_ALLOCATION_SAMPLE,
            Type.OBJECT_ALLOCATION_IN_NEW_TLAB,
            Type.OBJECT_ALLOCATION_OUTSIDE_TLAB,
            Type.THREAD_PARK,
            Type.THREAD_SLEEP,
            Type.JAVA_MONITOR_ENTER,
            Type.JAVA_MONITOR_WAIT);

    private final ProfileEventTypeRepository eventTypeRepository;
    private final GraphGenerator generator;

    public PrimaryFlamegraphManager(
            ProfileEventTypeRepository eventTypeRepository,
            GraphGenerator generator) {

        this.eventTypeRepository = eventTypeRepository;
        this.generator = generator;
    }

    @Override
    public List<EventSummaryResult> eventSummaries() {
        return eventTypeRepository.eventSummaries(SUPPORTED_EVENTS).stream()
                .filter(eventSummary -> eventSummary.samples() > 0)
                .map(EventSummaryResult::new)
                .toList();
    }

    @Override
    public byte[] generate(GraphParameters params) {
        LOG.debug("Generating flamegraph: eventType={} graphType={}", params.eventType(), params.graphType());
        long startTime = System.nanoTime();
        byte[] result = generator.generate(adjustParams(params));
        LOG.debug("Flamegraph generated: eventType={} durationMs={}", params.eventType(), Duration.ofNanos(System.nanoTime() - startTime).toMillis());
        return result;
    }

    private GraphParameters adjustParams(GraphParameters params) {
        // Adjust the useWeight parameter based on event type if not explicitly set
        return params.toBuilder()
                .withUseWeight(resolveWeight(params))
                .build();
    }

    /**
     * By default, use weight for allocation and blocking events if the weight is not explicitly specified.
     *
     * @param params original graph parameters
     * @return true if weight should be used, false otherwise
     */
    private static boolean resolveWeight(GraphParameters params) {
        if (params.useWeight() == null) {
            return params.eventType().isAllocationEvent() || params.eventType().isBlockingEvent();
        }
        return params.useWeight();
    }
}
