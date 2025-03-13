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

package pbouda.jeffrey.provider.writer.sqlite.query;

import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.provider.api.query.DirectQueryBuilder;
import pbouda.jeffrey.provider.api.query.QueryBuilder;
import pbouda.jeffrey.provider.api.query.QueryBuilderFactory;

import java.util.List;

public class JdbcQueryBuilderFactory implements QueryBuilderFactory {

    private final String profileInfo;
    private final List<Type> types;

    public JdbcQueryBuilderFactory(String profileInfo, List<Type> types) {
        this.profileInfo = profileInfo;
        this.types = types;
    }

    @Override
    //language=sql
    public QueryBuilder newTimeseriesQueryBuilder(boolean useWeight) {
        String fields = useWeight
                ? "events.timestamp, sum(events.weight) AS value"
                : "events.timestamp, sum(events.samples) AS value";

        return new DirectQueryBuilder(
                "SELECT " + fields + " FROM events WHERE events.type IN " + QueryBuilderUtils.eventTypesIn(types));
    }

    @Override
    public QueryBuilder newTimeseriesWithStacktracesQueryBuilder() {
        return null;
    }

    @Override
    public QueryBuilder newFlamegraphQueryBuilder() {
        return null;
    }

    @Override
    public QueryBuilder newFlamegraphWithThreadsQueryBuilder() {
        return null;
    }
}
