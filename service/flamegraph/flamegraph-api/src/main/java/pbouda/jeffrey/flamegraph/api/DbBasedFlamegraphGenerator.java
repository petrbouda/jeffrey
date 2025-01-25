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

package pbouda.jeffrey.flamegraph.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.RowCallbackHandler;
import pbouda.jeffrey.common.analysis.marker.Marker;
import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.flamegraph.FlameGraphBuilder;
import pbouda.jeffrey.flamegraph.GraphGenerator;
import pbouda.jeffrey.flamegraph.collector.FrameCollectorFactories;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.processor.EventProcessors;
import pbouda.jeffrey.frameir.record.SimpleRecord;
import pbouda.jeffrey.frameir.tree.SimpleTreeBuilder;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import pbouda.jeffrey.persistence.profile.EventsReadRepository;
import pbouda.jeffrey.persistence.profile.model.StacktraceRecord;
import pbouda.jeffrey.persistence.profile.parser.DbJfrStackTrace;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

public class DbBasedFlamegraphGenerator implements GraphGenerator {

    private final EventsReadRepository eventsReadRepository;

    public DbBasedFlamegraphGenerator(EventsReadRepository eventsReadRepository) {
        this.eventsReadRepository = eventsReadRepository;
    }

    @Override
    public ObjectNode generate(Config config) {
        return generate(config, List.of());
    }

    @Override
    public ObjectNode generate(Config config, List<Marker> markers) {
        if (config.eventType().isAllocationEvent()) {
            return JdkRecordingIterators.automaticAndCollect(
                    config.primaryRecordings(),
                    EventProcessors.allocationSamples(config),
                    FrameCollectorFactories.allocJson(markers));
        } else if (config.eventType().isBlockingEvent()) {
            return JdkRecordingIterators.automaticAndCollect(
                    config.primaryRecordings(),
                    EventProcessors.blocking(config),
                    FrameCollectorFactories.blockingJson(markers));
        } else if (config.eventType().isWallClockSample()) {
            return JdkRecordingIterators.automaticAndCollect(
                    config.primaryRecordings(),
                    EventProcessors.wallClockSamples(config),
                    FrameCollectorFactories.simpleJson(markers));
//        } else if (config.eventType().isNativeMallocSample()) {
//            return JdkRecordingIterators.automaticAndCollect(
//                    config.primaryRecordings(),
//                    EventProcessors.mallocSamples(config),
//                    FrameCollectorFactories.allocJson(markers));
//        } else if (config.eventType().isNativeLeak()) {
//            return new NativeLeakEventProcessingIterator(config.primaryRecordings())
//                    .iterate(EventProcessors.nativeLeaks(config),
//                            FrameCollectorFactories.allocJson(markers));
        } else {
            return generateFlamegraph(config);
        }
    }

    private ObjectNode generateFlamegraph(Config config) {
        long start = System.nanoTime();

        SimpleRecordFrameBuilder frameBuilder = new SimpleRecordFrameBuilder();
        eventsReadRepository.consumeStacktraces(config.eventType(), frameBuilder);

        System.out.println("Time: " + Duration.ofNanos(System.nanoTime() - start).toMillis());

        return new FlameGraphBuilder(false)
                .build(frameBuilder.get());
    }

    private static class SimpleRecordFrameBuilder implements RowCallbackHandler {

        private final SimpleTreeBuilder treeBuilder = new SimpleTreeBuilder(false, true);

        @Override
        public void processRow(ResultSet rs) throws SQLException {
            long samples = rs.getLong("samples");
            long weight = rs.getLong("weight");
            String frames = rs.getString("frames");
            treeBuilder.addRecord(new SimpleRecord(new DbJfrStackTrace(frames), null, samples, weight));
        }

        public Frame get() {
            return treeBuilder.build();
        }
    }
}
