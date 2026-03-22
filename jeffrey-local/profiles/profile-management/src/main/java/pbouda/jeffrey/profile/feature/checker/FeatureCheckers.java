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

package pbouda.jeffrey.profile.feature.checker;

import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.profile.feature.FeatureType;

import java.util.List;

public abstract class FeatureCheckers {

    private static final List<Type> JDBC_STATEMENT_TYPES = List.of(
            Type.JDBC_INSERT,
            Type.JDBC_UPDATE,
            Type.JDBC_DELETE,
            Type.JDBC_QUERY,
            Type.JDBC_EXECUTE,
            Type.JDBC_STREAM);

    private static final List<Type> JDBC_POOL_TYPES = List.of(
            Type.JDBC_POOL_STATISTICS,
            Type.ACQUIRING_POOLED_JDBC_CONNECTION_TIMEOUT,
            Type.POOLED_JDBC_CONNECTION_ACQUIRED,
            Type.POOLED_JDBC_CONNECTION_BORROWED,
            Type.POOLED_JDBC_CONNECTION_CREATED);

    public static final FeatureChecker HTTP_SERVER_DASHBOARD =
            new SamplesFeatureChecker(FeatureType.HTTP_SERVER_DASHBOARD, Type.HTTP_SERVER_EXCHANGE);

    public static final FeatureChecker HTTP_CLIENT_DASHBOARD =
            new SamplesFeatureChecker(FeatureType.HTTP_CLIENT_DASHBOARD, Type.HTTP_CLIENT_EXCHANGE);

    public static final FeatureChecker JDBC_STATEMENTS_DASHBOARD =
            new SamplesFeatureChecker(FeatureType.JDBC_STATEMENTS_DASHBOARD, JDBC_STATEMENT_TYPES);

    public static final FeatureChecker JDBC_POOL_DASHBOARD =
            new SamplesFeatureChecker(FeatureType.JDBC_POOL_DASHBOARD, JDBC_POOL_TYPES);

    public static final FeatureChecker TRACING_DASHBOARD =
            new SamplesFeatureChecker(FeatureType.TRACING_DASHBOARD, Type.METHOD_TRACE);
}
