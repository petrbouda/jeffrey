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

import cafe.jeffrey.provider.profile.api.*;

import cafe.jeffrey.shared.common.model.Type;

import java.util.List;

public class QueryBuilderFactoryResolverImpl implements QueryBuilderFactoryResolver {

    private final SQLFormatter sqlFormatter;
    private final ComplexQueries defaultComplexQueries;
    private final ComplexQueries nativeComplexQueries;

    public QueryBuilderFactoryResolverImpl(
            SQLFormatter sqlFormatter,
            ComplexQueries defaultComplexQueries,
            ComplexQueries nativeComplexQueries) {

        this.sqlFormatter = sqlFormatter;
        this.defaultComplexQueries = defaultComplexQueries;
        this.nativeComplexQueries = nativeComplexQueries;
    }

    @Override
    public QueryBuilderFactory resolve(List<Type> eventTypes) {
        if (eventTypes.size() == 1 && eventTypes.getFirst() == Type.NATIVE_LEAK) {
            return new NativeLeakQueryBuilderFactory(sqlFormatter, nativeComplexQueries);
        } else {
            return new DefaultQueryBuilderFactory(sqlFormatter, defaultComplexQueries);
        }
    }
}
