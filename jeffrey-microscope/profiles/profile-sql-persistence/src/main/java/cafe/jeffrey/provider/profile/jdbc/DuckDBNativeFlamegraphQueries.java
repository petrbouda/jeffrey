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
import cafe.jeffrey.shared.common.model.EventTypeName;

public class DuckDBNativeFlamegraphQueries implements ComplexQueries.Flamegraph {

    //language=SQL
    public static final String FREE_EVENT_EXISTS = """
            AND NOT EXISTS (
                SELECT 1 FROM events eFree
                WHERE eFree.event_type = 'profiler.Free'
                  AND e.weight_entity = eFree.weight_entity
            )
            """;

    private static final DuckDBFlamegraphQueries QUERIES =
            DuckDBFlamegraphQueries.of(EventTypeName.MALLOC, FREE_EVENT_EXISTS);

    @Override
    public String simple(EventQueryConfigurer configurer) {
        return QUERIES.simple(configurer);
    }

    @Override
    public String byWeight(EventQueryConfigurer configurer) {
        return QUERIES.byWeight(configurer);
    }

    @Override
    public String byThread(EventQueryConfigurer configurer) {
        return QUERIES.byThread(configurer);
    }

    @Override
    public String byThreadAndWeight(EventQueryConfigurer configurer) {
        return QUERIES.byThreadAndWeight(configurer);
    }
}
