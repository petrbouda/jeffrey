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
