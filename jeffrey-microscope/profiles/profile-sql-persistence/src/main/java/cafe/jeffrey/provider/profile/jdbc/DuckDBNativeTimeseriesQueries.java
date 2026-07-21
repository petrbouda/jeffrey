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

public class DuckDBNativeTimeseriesQueries implements ComplexQueries.Timeseries {

    private static final DuckDBTimeseriesQueries QUERIES = DuckDBTimeseriesQueries.of(
            EventTypeName.MALLOC, DuckDBNativeFlamegraphQueries.FREE_EVENT_EXISTS);

    @Override
    public String simple(EventQueryConfigurer configurer) {
        return QUERIES.simple(configurer);
    }

    @Override
    public String simpleSearch(EventQueryConfigurer configurer) {
        return QUERIES.simpleSearch(configurer);
    }

    @Override
    public String filterable(EventQueryConfigurer configurer) {
        return QUERIES.filterable(configurer);
    }

    @Override
    public String frameBased(EventQueryConfigurer configurer) {
        return QUERIES.frameBased(configurer);
    }

    @Override
    public String frameBasedEvents(EventQueryConfigurer configurer) {
        return QUERIES.frameBasedEvents(configurer);
    }
}
