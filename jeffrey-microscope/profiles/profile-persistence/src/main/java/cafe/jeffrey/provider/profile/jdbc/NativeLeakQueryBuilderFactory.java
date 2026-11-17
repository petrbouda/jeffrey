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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.sql.SQLBuilder;

import java.util.List;

public class NativeLeakQueryBuilderFactory implements QueryBuilderFactory {

    private final SQLFormatter sqlFormatter;
    private final ComplexQueries complexQueries;

    //language=sql
    private static final String FREE_EVENT_EXISTS = """
            SELECT 1 FROM events eFree
            WHERE eFree.event_type = 'profiler.Free'
                AND events.weight_entity = eFree.weight_entity
            """;

    private final SQLBuilder builder;

    public NativeLeakQueryBuilderFactory(SQLFormatter sqlFormatter, ComplexQueries complexQueries) {
        this.sqlFormatter = sqlFormatter;
        this.complexQueries = complexQueries;
        this.builder = new SQLBuilder()
                .where(SQLBuilder.notExists(FREE_EVENT_EXISTS));
    }

    @Override
    public GenericQueryBuilder createGenericQueryBuilder(EventQueryConfigurer configurer) {
        return new GenericQueryBuilder(sqlFormatter, configurer, List.of(Type.MALLOC))
                .merge(builder);
    }

    @Override
    public ComplexQueries complexQueries() {
        return complexQueries;
    }
}
