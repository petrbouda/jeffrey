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

package cafe.jeffrey.provider.profile.api;

import cafe.jeffrey.shared.common.model.SpanInterval;
import cafe.jeffrey.shared.common.model.StacktraceTag;
import cafe.jeffrey.shared.common.model.StacktraceType;
import cafe.jeffrey.shared.common.model.ThreadInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;

import java.util.List;

public class EventQueryConfigurer {

    /**
     * Equality filter on a single top-level JSON field of the event. The filter is pushed down
     * into SQL by the persistence layer instead of being evaluated on streamed rows in Java.
     *
     * @param field name of the top-level JSON field (e.g. {@code poolName})
     * @param value expected string value of the field
     */
    public record JsonFieldFilter(String field, String value) {

        public JsonFieldFilter {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("JSON field name must not be blank");
            }
            if (value == null) {
                throw new IllegalArgumentException("JSON field value must not be null");
            }
        }
    }

    private List<StacktraceType> stacktraceTypes;
    private List<StacktraceTag> stacktraceTags;
    private boolean withJsonFields;
    private JsonFieldFilter jsonFieldFilter;
    private boolean withEventTypeInfo;
    private Boolean useWeight;
    private boolean withThreads;
    private ThreadInfo specifiedThread;
    private List<Type> eventTypes;
    private RelativeTimeRange timeRange;
    private String searchPattern;
    private List<SpanInterval> spanIntervals;
    private boolean orderedByTime;
    private boolean allEventTypes;

    /**
     * Include all types of events in the event-stream.
     *
     * @return instance of the event-stream configurer
     */
    public EventQueryConfigurer withEventTypes(List<Type> eventTypes) {
        this.eventTypes = eventTypes;
        return this;
    }

    /**
     * Aggregate across every event type, ignoring the configured event type filter. Used by the
     * recording-overview activity query to produce a total-activity timeseries.
     *
     * @return instance of the event-stream configurer
     */
    public EventQueryConfigurer withAllEventTypes(boolean allEventTypes) {
        this.allEventTypes = allEventTypes;
        return this;
    }

    /**
     * Include the specified type of event in the event-stream.
     *
     * @param eventType type of event
     * @return instance of the event-stream configurer
     */
    public EventQueryConfigurer withEventType(Type eventType) {
        this.eventTypes = List.of(eventType);
        return this;
    }

    /**
     * Limit the event-stream to the specified time range.
     *
     * @param timeRange time range
     * @return instance of the event-stream configurer
     */
    public EventQueryConfigurer withTimeRange(RelativeTimeRange timeRange) {
        this.timeRange = timeRange;
        return this;
    }

    /**
     * Filter the event-stream with the specified type of stacktraces.
     *
     * @param stacktraceType type of stacktraces
     * @return instance of the event-stream configurer
     */
    public EventQueryConfigurer filterStacktraceType(StacktraceType stacktraceType) {
        return filterStacktraceTypes(List.of(stacktraceType));
    }

    /**
     * Filter the event-stream with the specified types of stacktraces.
     *
     * @param stacktraceTypes types of stacktraces
     * @return instance of the event-stream configurer
     */
    public EventQueryConfigurer filterStacktraceTypes(List<StacktraceType> stacktraceTypes) {
        this.stacktraceTypes = stacktraceTypes;
        return this;
    }

    /**
     * Filter the event-stream with the specified tags of stacktraces.
     *
     * @param tags tags of stacktraces
     * @return instance of the event-stream configurer
     */
    public EventQueryConfigurer filterStacktraceTags(List<StacktraceTag> tags) {
        this.stacktraceTags = tags;
        return this;
    }

    /**
     * Automatically adds the thread information to the event-stream.
     *
     * @return instance of the event-stream configurer
     */
    public EventQueryConfigurer withThreads() {
        this.withThreads = true;
        return this;
    }

    /**
     * Automatically adds the thread information to the event-stream.
     *
     * @return instance of the event-stream configurer
     */
    public EventQueryConfigurer withThreads(boolean withThreads) {
        this.withThreads = withThreads;
        return this;
    }

    /**
     * Limit the event-stream to the specified threads.
     *
     * @param threadInfo thread information
     * @return instance of the event-stream configurer
     */
    public EventQueryConfigurer withSpecifiedThread(ThreadInfo threadInfo) {
        if (threadInfo != null) {
            this.withThreads = true;
            this.specifiedThread = threadInfo;
        }
        return this;
    }

    /**
     * Include the event type information in the event-stream.
     *
     * @return instance of the event-stream configurer
     */
    public EventQueryConfigurer withEventTypeInfo() {
        this.withEventTypeInfo = true;
        return this;
    }

    /**
     * Include the JSON fields in the event-stream.
     *
     * @return instance of the event-stream configurer
     */
    public EventQueryConfigurer withJsonFields() {
        this.withJsonFields = true;
        return this;
    }

    /**
     * Keep only events whose top-level JSON field equals the given value. The filter is pushed
     * down into SQL by the persistence layer.
     *
     * @param field name of the top-level JSON field (e.g. {@code poolName})
     * @param value expected string value of the field
     * @return instance of the event-stream configurer
     */
    public EventQueryConfigurer withJsonFieldEquals(String field, String value) {
        this.jsonFieldFilter = new JsonFieldFilter(field, value);
        return this;
    }

    /**
     * Event-stream will use weight instead of samples, if the output entity supports only one type of value.
     *
     * @return instance of the event-stream configurer
     */
    public EventQueryConfigurer withWeight(boolean useWeight) {
        this.useWeight = useWeight;
        return this;
    }

    public EventQueryConfigurer withSearchPattern(String searchPattern) {
        this.searchPattern = searchPattern;
        return this;
    }

    /**
     * Scope the event-stream to the union of the given span windows. A sample is kept only if it was
     * taken on a span's thread within that span's time window; samples are matched at most once even
     * if several spans overlap. An empty/null list applies no span scoping.
     *
     * @param spanIntervals per-span (thread, time-window) intervals
     * @return instance of the event-stream configurer
     */
    public EventQueryConfigurer withSpanIntervals(List<SpanInterval> spanIntervals) {
        this.spanIntervals = spanIntervals;
        return this;
    }

    /**
     * Stream the events in chronological order (by start timestamp). Consumers that pair or
     * sequence events (e.g. start/end matching) must opt in — the physical order of the events
     * table is the clustering order, not guaranteed to be chronological across event types.
     *
     * @return instance of the event-stream configurer
     */
    public EventQueryConfigurer orderedByTime() {
        this.orderedByTime = true;
        return this;
    }

    public List<Type> eventTypes() {
        return eventTypes;
    }

    public boolean allEventTypes() {
        return allEventTypes;
    }

    public RelativeTimeRange timeRange() {
        return timeRange;
    }

    public List<StacktraceType> filterStacktraceTypes() {
        return stacktraceTypes;
    }

    public List<StacktraceTag> filterStacktraceTags() {
        return stacktraceTags;
    }

    public boolean jsonFields() {
        return withJsonFields;
    }

    public JsonFieldFilter jsonFieldFilter() {
        return jsonFieldFilter;
    }

    public boolean eventTypeInfo() {
        return withEventTypeInfo;
    }

    public boolean useWeight() {
        return useWeight != null && useWeight;
    }

    public boolean threads() {
        return withThreads;
    }

    public ThreadInfo specifiedThread() {
        return specifiedThread;
    }

    public String searchPattern() {
        return searchPattern;
    }

    public List<SpanInterval> spanIntervals() {
        return spanIntervals;
    }

    public boolean isOrderedByTime() {
        return orderedByTime;
    }
}
