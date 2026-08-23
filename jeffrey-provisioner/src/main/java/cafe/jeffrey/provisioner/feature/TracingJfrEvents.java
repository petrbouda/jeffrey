/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.provisioner.feature;

import cafe.jeffrey.shared.common.model.EventTypeName;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The JDK events a traced session needs, and how each one is recorded.
 *
 * <p>The stock {@code default.jfc} the profiler records with keeps these events well above the
 * durations a trace is read at — 20 ms for lock waits and parks, 1 ms for socket and file I/O on
 * Java 25 — so a trace shows the method that blocked but not what it blocked on. These are the same
 * event types the Tracing view promotes into leaf spans, named from {@link EventTypeName} so the two
 * lists cannot drift into disagreeing about an event's spelling.
 *
 * <p>I/O is recorded in full ({@code 0ms}): every socket and file operation is a call the
 * application made on purpose, and its duration is the answer the waterfall exists to show.
 * Blocking is recorded from {@code 1ms} up, because a parked or waiting thread is also how an idle
 * thread pool spends its life — at {@code 0ms} that churn would outweigh the waits worth reading.
 */
public abstract class TracingJfrEvents {

    private static final String IO_THRESHOLD = "0ms";
    private static final String BLOCKING_THRESHOLD = "1ms";

    private static final String SETTING_SEPARATOR = ",";
    private static final String EVENT_SETTING_SEPARATOR = "#";
    private static final String ASSIGN = "=";
    private static final String ENABLED_SETTING = "enabled";
    private static final String THRESHOLD_SETTING = "threshold";
    private static final String THROTTLE_SETTING = "throttle";
    private static final String ENABLED_VALUE = "true";
    private static final String THROTTLE_OFF = "off";

    /**
     * One event, the threshold it is recorded at, and whether its rate limit is lifted.
     *
     * <p>Rendered with an {@code enabled} pair as well: JFR takes the most verbose value across the
     * active recordings per setting, so a threshold alone would be ignored for an event that a
     * custom profiler configuration had switched off.
     */
    record EventThreshold(String eventType, String threshold, boolean unthrottled) {

        /**
         * Recorded in full, and its rate limit lifted. Java 25 rate-limits these to 100 events a
         * second in {@code default.jfc}, which a threshold does not lift.
         *
         * <p>The lift only reaches as far as the other recordings allow. Unlike a threshold, where
         * JFR takes the most verbose value across the active recordings, a rate limit resolves to
         * the most restrictive one — so while the profiler records with a configuration that
         * throttles, that cap holds for everyone and this setting has no effect. It applies when
         * the profiler runs a configuration that does not throttle, which is every JDK before 25.
         */
        static EventThreshold io(String eventType) {
            return new EventThreshold(eventType, IO_THRESHOLD, true);
        }

        /** Recorded from {@link #BLOCKING_THRESHOLD} up; these carry no rate limit to lift. */
        static EventThreshold blocking(String eventType) {
            return new EventThreshold(eventType, BLOCKING_THRESHOLD, false);
        }

        String render() {
            StringBuilder settings = new StringBuilder()
                    .append(setting(ENABLED_SETTING, ENABLED_VALUE))
                    .append(SETTING_SEPARATOR)
                    .append(setting(THRESHOLD_SETTING, threshold));
            if (unthrottled) {
                settings.append(SETTING_SEPARATOR).append(setting(THROTTLE_SETTING, THROTTLE_OFF));
            }
            return settings.toString();
        }

        private String setting(String name, String value) {
            return eventType + EVENT_SETTING_SEPARATOR + name + ASSIGN + value;
        }
    }

    /**
     * {@code jdk.FileForce} is I/O but carries no {@code throttle} setting to switch off — naming
     * one it does not have costs a JFR warning at startup. {@code jdk.ZAllocationStall} is already
     * unthresholded in {@code default.jfc} and is listed so the settings stand on their own.
     */
    static final List<EventThreshold> DEFAULT_EVENTS = List.of(
            EventThreshold.io(EventTypeName.SOCKET_READ),
            EventThreshold.io(EventTypeName.SOCKET_WRITE),
            EventThreshold.io(EventTypeName.FILE_READ),
            EventThreshold.io(EventTypeName.FILE_WRITE),
            new EventThreshold(EventTypeName.FILE_FORCE, IO_THRESHOLD, false),
            EventThreshold.blocking(EventTypeName.JAVA_MONITOR_ENTER),
            EventThreshold.blocking(EventTypeName.JAVA_MONITOR_WAIT),
            EventThreshold.blocking(EventTypeName.THREAD_PARK),
            EventThreshold.blocking(EventTypeName.THREAD_SLEEP),
            EventThreshold.blocking(EventTypeName.VIRTUAL_THREAD_PINNED),
            new EventThreshold(EventTypeName.Z_ALLOCATION_STALL, IO_THRESHOLD, false));

    /** The built-in event settings, as the {@code -XX:StartFlightRecording} option spells them. */
    public static final String DEFAULT_SETTINGS = DEFAULT_EVENTS.stream()
            .map(EventThreshold::render)
            .collect(Collectors.joining(SETTING_SEPARATOR));
}
