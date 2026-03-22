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

package pbouda.jeffrey.provider.profile.query.builder;

import pbouda.jeffrey.provider.profile.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.profile.query.ComplexQueries;
import pbouda.jeffrey.provider.profile.query.GenericQueryBuilder;
import pbouda.jeffrey.provider.profile.query.SQLFormatter;

public class DefaultQueryBuilderFactory implements QueryBuilderFactory {

    private final SQLFormatter sqlFormatter;
    private final ComplexQueries complexQueries;

    public DefaultQueryBuilderFactory(SQLFormatter sqlFormatter, ComplexQueries complexQueries) {
        this.sqlFormatter = sqlFormatter;
        this.complexQueries = complexQueries;
    }

    @Override
    public GenericQueryBuilder createGenericQueryBuilder(EventQueryConfigurer configurer) {
        return new GenericQueryBuilder(sqlFormatter, configurer);
    }

    @Override
    public ComplexQueries complexQueries() {
        return complexQueries;
    }
}
