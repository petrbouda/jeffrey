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

package pbouda.jeffrey.provider.writer.sql.query.builder;

import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.writer.sql.query.ComplexQueries;
import pbouda.jeffrey.provider.writer.sql.query.GenericQueryBuilder;
import pbouda.jeffrey.provider.writer.sql.query.SQLFormatter;
import pbouda.jeffrey.sql.SQLBuilder;

import java.util.List;

public class NativeLeakQueryBuilderFactory implements QueryBuilderFactory {

    private final SQLFormatter sqlFormatter;
    private final ComplexQueries complexQueries;
    private final String profileId;

    //language=sql
    private static final String FREE_EVENT_EXISTS = """
            SELECT 1 FROM events eFree
            WHERE eFree.profile_id = '<<profile_id>>'
                AND eFree.event_type = 'profiler.Free'
                AND events.weight_entity = eFree.weight_entity
            """;

    private final SQLBuilder builder;

    public NativeLeakQueryBuilderFactory(SQLFormatter sqlFormatter, ComplexQueries complexQueries, String profileId) {
        this.sqlFormatter = sqlFormatter;
        this.complexQueries = complexQueries;
        this.profileId = profileId;
        this.builder = new SQLBuilder()
                .where(SQLBuilder.notExists(FREE_EVENT_EXISTS.replace("<<profile_id>>", profileId)));
    }

    @Override
    public GenericQueryBuilder createGenericQueryBuilder(EventQueryConfigurer configurer) {
        return new GenericQueryBuilder(sqlFormatter, profileId, configurer, List.of(Type.MALLOC))
                .merge(builder);
    }

    @Override
    public ComplexQueries complexQueries() {
        return complexQueries;
    }
}
