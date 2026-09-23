/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;

/**
 * SQL providers for the complex analytical queries (flamegraph, timeseries, sub-second). Every
 * method renders the SQL for one particular execution: the configurer decides which optional filter
 * clauses are spliced into the statement (see {@link EventQueryFilters}).
 */
public interface ComplexQueries {

    interface Flamegraph {

        String simple(EventQueryConfigurer configurer);

        String byWeight(EventQueryConfigurer configurer);

        String byThread(EventQueryConfigurer configurer);

        String byThreadAndWeight(EventQueryConfigurer configurer);
    }

    interface Timeseries {

        String simple(EventQueryConfigurer configurer);

        String simpleSearch(EventQueryConfigurer configurer);

        String filterable(EventQueryConfigurer configurer);

        String frameBased(EventQueryConfigurer configurer);

        /**
         * Like {@link #frameBased} but WITHOUT per-second bucketing: one {@code event_values} entry per
         * event ({@code (timestampFromStartMs, value)}), so a weighted export can emit one OTLP observation
         * per sample and preserve the exact sample count. The {@code second} struct slot carries
         * milliseconds-from-start, not a second index.
         */
        String frameBasedEvents(EventQueryConfigurer configurer);
    }

    interface SubSecond {

        String simple(EventQueryConfigurer configurer);

    }

    Flamegraph flamegraph();

    Timeseries timeseries();

    SubSecond subSecond();
}
