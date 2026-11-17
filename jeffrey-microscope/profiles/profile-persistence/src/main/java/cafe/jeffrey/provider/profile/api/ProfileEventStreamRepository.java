/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.provider.profile.api;

import cafe.jeffrey.provider.profile.api.*;

public interface ProfileEventStreamRepository {

    <T> T genericStreaming(EventQueryConfigurer configurer, RecordBuilder<GenericRecord, T> builder);

    <T> T subSecondStreamer(EventQueryConfigurer configurer, RecordBuilder<SubSecondRecord, T> builder);

    <T> T timeseriesStreamer(EventQueryConfigurer configurer, RecordBuilder<TimeseriesRecord, T> builder);

    <T> T timeseriesSearchingStreamer(EventQueryConfigurer configurer, RecordBuilder<TimeseriesSearchRecord, T> builder);

    <T> T filterableTimeseriesStreamer(EventQueryConfigurer configurer, RecordBuilder<SecondValue, T> builder);

    <T> T frameBasedTimeseriesStreamer(EventQueryConfigurer configurer, RecordBuilder<TimeseriesRecord, T> builder);

    /**
     * Like {@link #frameBasedTimeseriesStreamer} but emits one {@link SecondValue} per original event instead
     * of per-second buckets — the {@code SecondValue.second} slot then carries milliseconds-from-start, not a
     * second index. Used by the weighted OTLP export so the exact per-sample count round-trips.
     */
    <T> T frameBasedEventStreamer(EventQueryConfigurer configurer, RecordBuilder<TimeseriesRecord, T> builder);

    <T> T flamegraphStreamer(EventQueryConfigurer configurer, RecordBuilder<FlamegraphRecord, T> builder);

    /**
     * Where the events of the configured type were issued from, by the protocol {@link CallSiteReader} names:
     * the distinct stacks first, each charged to a path by the reader, then complete totals and group
     * figures with only the selected sites — per trace included — and each operation's events per stack.
     * Only the configurer's single event type, time range, span scope, stacktrace filters and JSON field
     * filter apply. Selection caps never change the complete aggregates.
     *
     * @param groupField the event field whose value the sites are split by, or {@code null} for none
     * @param selection  the ranked sites, or ranked groups with a cap on each group's sites
     */
    <T> T callSites(EventQueryConfigurer configurer, String groupField,
                    CallSiteSelection selection, CallSiteReader<T> reader);
}
