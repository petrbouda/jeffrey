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

package pbouda.jeffrey.repository.parser;

import jdk.jfr.consumer.EventStream;
import pbouda.jeffrey.shared.model.Type;
import pbouda.jeffrey.shared.model.repository.RecordingSession;
import pbouda.jeffrey.shared.model.time.AbsoluteTimeRange;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;

/**
 * Iterator for JFR repository events using JDK's EventStream.openRepository().
 * Supports time interval filtering to limit which events are processed.
 *
 * <p>IMPORTANT: When reading from a live repository (where the JVM is still running
 * and emitting events), you MUST set an endTime to prevent the stream from blocking
 * forever waiting for new events.
 *
 * @param <PARTIAL> intermediate result type from processing events
 * @param <RESULT>  final result type after applying the collector's finisher
 */
public class JdkRepositoryIterator<PARTIAL, RESULT> implements RepositoryIterator<PARTIAL, RESULT> {

    private final RecordingSession session;
    private final EventProcessor<PARTIAL> processor;
    private final AbsoluteTimeRange timeRange;
    private final Clock clock;

    /**
     * Creates an iterator for a JFR repository with time interval filtering.
     *
     * @param session   path to the JFR repository directory
     * @param processor the event processor to handle events
     * @param timeRange time range for filtering events (start/end can be Instant.MIN/MAX for no bounds)
     * @param clock     clock for determining current time (for testability)
     */
    public JdkRepositoryIterator(
            RecordingSession session,
            EventProcessor<PARTIAL> processor,
            AbsoluteTimeRange timeRange,
            Clock clock) {

        this.session = session;
        this.processor = processor;
        this.timeRange = timeRange;
        this.clock = clock;
    }

    @Override
    public RESULT collect(Collector<PARTIAL, RESULT> collector) {
        iterate(processor);
        return collector.finisher(processor.get());
    }

    @Override
    public PARTIAL partialCollect(Collector<PARTIAL, ?> collector) {
        iterate(processor);
        return processor.get();
    }

    private void iterate(EventProcessor<PARTIAL> eventProcessor) {
        if (!Files.exists(session.absoluteStreamingPath())) {
            throw new RuntimeException("Repository does not exist: " + session);
        }

        ProcessableEvents processableEvents = eventProcessor.processableEvents();
        eventProcessor.onStart();

        try (EventStream stream = EventStream.openRepository(session.absoluteStreamingPath())) {
            // Set time bounds for the stream
            if (timeRange.isStartUsed()) {
                stream.setStartTime(timeRange.start());
            }

            // CRITICAL: Always set endTime to prevent blocking on live repositories.
            // If the JVM is still running and emitting events, stream.start() would
            // block forever waiting for new events without an endTime.
            Instant effectiveEndTime = timeRange.isEndUsed() ? timeRange.end() : clock.instant();
            stream.setEndTime(effectiveEndTime);

            stream.onMetadata(metadata -> eventProcessor.onMetadata(metadata.getEventTypes()));

            if (processableEvents.isProcessableAll()) {
                stream.onEvent(eventProcessor::onEvent);
            } else {
                for (Type event : processableEvents.events()) {
                    stream.onEvent(event.code(), eventProcessor::onEvent);
                }
            }

            stream.start();
            eventProcessor.onComplete();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read repository: " + session, e);
        }
    }
}
