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
}
