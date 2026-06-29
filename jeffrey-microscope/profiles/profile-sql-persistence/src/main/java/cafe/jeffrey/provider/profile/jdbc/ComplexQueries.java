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
         * Total-activity timeseries for the recording overview: bucketed {@code SUM} of the value,
         * with no stacktrace join (so stackless event types such as GC are included) and an
         * event-type filter that is omitted when {@link EventQueryConfigurer#allEventTypes()}.
         */
        String overview(EventQueryConfigurer configurer);
    }

    interface SubSecond {

        String simple(EventQueryConfigurer configurer);

    }

    Flamegraph flamegraph();

    Timeseries timeseries();

    SubSecond subSecond();
}
