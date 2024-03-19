package pbouda.jeffrey.graph.diff;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.TimeRange;
import pbouda.jeffrey.common.EventType;

import java.nio.file.Path;
import java.time.Instant;

public interface DiffgraphGenerator {

    record Request(
            Path primaryPath,
            Instant primaryStart,
            Path secondaryPath,
            Instant secondaryStart,
            EventType eventType,
            TimeRange timeRange) {

        public Request(
                Path primaryPath,
                Instant primaryStart,
                Path secondaryPath,
                Instant secondaryStart,
                EventType eventType) {

            this(primaryPath, primaryStart, secondaryPath, secondaryStart, eventType, null);
        }

        public Request toAbsoluteTime() {
            if (timeRange != null && !timeRange.absoluteTime()) {
                var absoluteTimeRange = new TimeRange(
                        primaryStart.plusMillis(timeRange.start()).toEpochMilli(),
                        primaryStart.plusMillis(timeRange.end()).toEpochMilli(),
                        true);

                return new Request(
                        primaryPath,
                        primaryStart,
                        secondaryPath,
                        secondaryStart,
                        eventType,
                        absoluteTimeRange);
            } else {
                return this;
            }
        }

        public Request shiftTimeRange(long timeShiftInMillis) {
            TimeRange shiftedTimeRange;
            if (timeRange != null) {
                shiftedTimeRange = new TimeRange(
                        timeRange.start() + timeShiftInMillis,
                        timeRange.end() + timeShiftInMillis,
                        timeRange.absoluteTime());
            } else {
                shiftedTimeRange = null;
            }

            return new Request(
                    primaryPath,
                    primaryStart,
                    secondaryPath,
                    secondaryStart,
                    eventType,
                    shiftedTimeRange);
        }
    }

    ObjectNode generate(Request request);

}
