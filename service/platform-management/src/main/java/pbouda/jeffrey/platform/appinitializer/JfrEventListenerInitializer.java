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

package pbouda.jeffrey.platform.appinitializer;

import cafe.jeffrey.jfr.events.http.HttpClientExchangeEvent;
import cafe.jeffrey.jfr.events.http.HttpServerExchangeEvent;
import cafe.jeffrey.jfr.events.jdbc.pool.AcquiringPooledJdbcConnectionTimeoutEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.*;
import jdk.jfr.Timespan;
import jdk.jfr.consumer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import pbouda.jeffrey.common.DurationUtils;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sql.repository.JdbcProfileEventStreamRepository;

import java.time.Duration;
import java.util.Optional;
import java.util.StringJoiner;

public class JfrEventListenerInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JfrEventListenerInitializer.class);

    private final Duration threshold;

    public JfrEventListenerInitializer(Duration threshold) {
        this.threshold = threshold;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        var rs = new RecordingStream();
        Runtime.getRuntime().addShutdownHook(new Thread(rs::close));

        rs.onEvent(HttpClientExchangeEvent.NAME, this::logEventWithFields);
        rs.onEvent(HttpServerExchangeEvent.NAME, this::logEventWithFields);
        rs.onEvent(AcquiringPooledJdbcConnectionTimeoutEvent.NAME, this::logEventWithFields);
//        rs.onEvent(PooledConnectionAcquiredEvent.NAME, this::logEventWithFields);
//        rs.onEvent(PooledConnectionBorrowedEvent.NAME, this::logEventWithFields);
//        rs.onEvent(PooledConnectionCreatedEvent.NAME, this::logEventWithFields);
//        rs.onEvent(PoolStatisticsEvent.NAME, this::logEventWithFields);
        rs.onEvent(JdbcQueryEvent.NAME, this::logEventWithFields);
        rs.onEvent(JdbcInsertEvent.NAME, this::logEventWithFields);
        rs.onEvent(JdbcUpdateEvent.NAME, this::logEventWithFields);
        rs.onEvent(JdbcDeleteEvent.NAME, this::logEventWithFields);
        rs.onEvent(JdbcExecuteEvent.NAME, this::logEventWithFields);
        rs.onEvent(JdbcStreamEvent.NAME, this::logEventWithFields);
        rs.startAsync();
    }

    /**
     * Consumes a JFR event and logs its name along with all fields in a single line.
     *
     * @param event The JFR recorded event to process
     */
    public void logEventWithFields(RecordedEvent event) {
        if (event.getDuration() == null || threshold == null || event.getDuration().compareTo(threshold) < 0) {
            return;
        }

        StringJoiner fields = new StringJoiner(", ", "[", "]");

        // Iterate through all fields in the event and add them to the StringJoiner
        for (var field : event.getFields()) {
            String fieldName = field.getName();
            Object fieldValue = event.getValue(fieldName);

            if (Timespan.class.getName().equals(field.getContentType())) {
                Duration duration = Duration.ofNanos((long) fieldValue);
                fields.add(fieldName + "=" + DurationUtils.format2parts(duration));
                continue;
            }

            // Special handling for thread fields - extract only the thread name
            if (fieldValue instanceof RecordedThread thread) {
                fields.add(fieldName + "=" + thread.getJavaName());

            } else if (fieldValue instanceof RecordedStackTrace stackTrace) {
                Optional<RecordedFrame> foundFrame = stackTrace.getFrames().stream()
                        .filter(JfrEventListenerInitializer::meaningfulJeffreyFrame)
                        .findFirst();

                if (foundFrame.isPresent()) {
                    RecordedMethod method = foundFrame.get().getMethod();
                    fields.add("jeffrey-frame=" + method.getType().getName() + "#" + method.getName());
                } else {
                    fields.add("jeffrey-frame=<no-jeffrey-frame>");
                }
            } else if (fieldValue != null) {
                fields.add(fieldName + "=" + fieldValue);
            }
        }

        // Log the event name and all its fields in a single line
        if (event.getDuration() != null && event.getDuration().compareTo(Duration.ofSeconds(1)) > 0) {
            LOGGER.warn("{} {}", event.getEventType().getName(), fields);
        } else {
            LOGGER.info("{} {}", event.getEventType().getName(), fields);
        }
    }

    private static boolean meaningfulJeffreyFrame(RecordedFrame frame) {
        if (frame.getMethod() == null) {
            return false;
        }
        String methodName = frame.getMethod().getType().getName();
        return methodName.startsWith("pbouda.jeffrey")
                && !(methodName.startsWith(JdbcProfileEventStreamRepository.class.getName())
                || methodName.startsWith(DatabaseClient.class.getName()));
    }
}
