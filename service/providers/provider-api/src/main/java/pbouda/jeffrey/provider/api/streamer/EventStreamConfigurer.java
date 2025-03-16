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

package pbouda.jeffrey.provider.api.streamer;

import pbouda.jeffrey.common.model.StacktraceTag;
import pbouda.jeffrey.common.model.StacktraceType;
import pbouda.jeffrey.common.model.ThreadInfo;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;

import java.util.List;

public class EventStreamConfigurer {

    private List<StacktraceType> stacktraceTypes;
    private List<StacktraceTag> stacktraceTags;
    private boolean withJsonFields;
    private boolean withEventTypeInfo;
    private boolean useWeight;
    private boolean withThreads;
    private boolean includeFrames;
    private ThreadInfo specifiedThread;
    private List<Type> eventTypes;
    private RelativeTimeRange timeRange;

    /**
     * Include all types of events in the event-stream.
     *
     * @return instance of the event-stream configurer
     */
    public EventStreamConfigurer withEventTypes(List<Type> eventTypes) {
        this.eventTypes = eventTypes;
        return this;
    }

    /**
     * Include the specified type of event in the event-stream.
     *
     * @param eventType type of event
     * @return instance of the event-stream configurer
     */
    public EventStreamConfigurer withEventType(Type eventType) {
        this.eventTypes = List.of(eventType);
        return this;
    }

    /**
     * Limit the event-stream to the specified time range.
     *
     * @param timeRange time range
     * @return instance of the event-stream configurer
     */
    public EventStreamConfigurer withTimeRange(RelativeTimeRange timeRange) {
        this.timeRange = timeRange;
        return this;
    }

    /**
     * Include frames to the output entity.
     *
     * @return instance of the event-stream configurer
     */
    public EventStreamConfigurer withIncludeFrames() {
        return withIncludeFrames(true);
    }

    /**
     * Include frames to the output entity.
     *
     * @param withStacktraceFrames boolean value whether to include frames
     * @return instance of the event-stream configurer
     */
    public EventStreamConfigurer withIncludeFrames(boolean withStacktraceFrames) {
        this.includeFrames = withStacktraceFrames;
        return this;
    }

    /**
     * Filter the event-stream with the specified types of stacktraces.
     *
     * @param stacktraceTypes types of stacktraces
     * @return instance of the event-stream configurer
     */
    public EventStreamConfigurer filterStacktraceTypes(List<StacktraceType> stacktraceTypes) {
        this.stacktraceTypes = stacktraceTypes;
        return this;
    }

    /**
     * Filter the event-stream with the specified tags of stacktraces.
     *
     * @param tags tags of stacktraces
     * @return instance of the event-stream configurer
     */
    public EventStreamConfigurer filterStacktraceTags(List<StacktraceTag> tags) {
        this.stacktraceTags = tags;
        return this;
    }

    /**
     * Automatically adds the thread information to the event-stream.
     *
     * @return instance of the event-stream configurer
     */
    public EventStreamConfigurer withThreads() {
        this.withThreads = true;
        return this;
    }

    /**
     * Automatically adds the thread information to the event-stream.
     *
     * @return instance of the event-stream configurer
     */
    public EventStreamConfigurer withThreads(boolean withThreads) {
        this.withThreads = withThreads;
        return this;
    }

    /**
     * Limit the event-stream to the specified threads.
     *
     * @param threadInfo thread information
     * @return instance of the event-stream configurer
     */
    public EventStreamConfigurer withSpecifiedThread(ThreadInfo threadInfo) {
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
    public EventStreamConfigurer withEventTypeInfo() {
        this.withEventTypeInfo = true;
        return this;
    }

    /**
     * Include the JSON fields in the event-stream.
     *
     * @return instance of the event-stream configurer
     */
    public EventStreamConfigurer withJsonFields() {
        this.withJsonFields = true;
        return this;
    }

    /**
     * Event-stream will use weight instead of samples, if the output entity supports only one type of value.
     *
     * @return instance of the event-stream configurer
     */
    public EventStreamConfigurer withWeight() {
        useWeight = true;
        return this;
    }

    /**
     * Event-stream will use weight instead of samples, if the output entity supports only one type of value.
     *
     * @return instance of the event-stream configurer
     */
    public EventStreamConfigurer withWeight(boolean useWeight) {
        this.useWeight = useWeight;
        return this;
    }

    public List<Type> eventTypes() {
        return eventTypes;
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

    public boolean includeFrames() {
        return includeFrames;
    }

    public boolean jsonFields() {
        return withJsonFields;
    }

    public boolean eventTypeInfo() {
        return withEventTypeInfo;
    }

    public boolean useWeight() {
        return useWeight;
    }

    public boolean threads() {
        return withThreads;
    }

    public ThreadInfo specifiedThread() {
        return specifiedThread;
    }
}
