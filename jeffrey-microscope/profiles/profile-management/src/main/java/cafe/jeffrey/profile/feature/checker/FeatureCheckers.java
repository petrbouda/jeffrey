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

package cafe.jeffrey.profile.feature.checker;

import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.feature.FeatureType;

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

    public static final FeatureChecker GRPC_SERVER_DASHBOARD =
            new SamplesFeatureChecker(FeatureType.GRPC_SERVER_DASHBOARD, Type.GRPC_SERVER_EXCHANGE);

    public static final FeatureChecker GRPC_CLIENT_DASHBOARD =
            new SamplesFeatureChecker(FeatureType.GRPC_CLIENT_DASHBOARD, Type.GRPC_CLIENT_EXCHANGE);

    public static final FeatureChecker JDBC_STATEMENTS_DASHBOARD =
            new SamplesFeatureChecker(FeatureType.JDBC_STATEMENTS_DASHBOARD, JDBC_STATEMENT_TYPES);

    public static final FeatureChecker JDBC_POOL_DASHBOARD =
            new SamplesFeatureChecker(FeatureType.JDBC_POOL_DASHBOARD, JDBC_POOL_TYPES);

    /**
     * JDK method timing and tracing (JEP 520), not distributed tracing — see
     * {@link TracesFeatureChecker} for the Traces section.
     */
    /*
     * Either event opens the dashboard. jdk.MethodTiming is enabled in both JFR configurations out
     * of the box and only needs a filter, while jdk.MethodTrace has to be switched on -- so a
     * recording carrying timings and no traces is the ordinary case, and gating on MethodTrace
     * alone hid a populated dashboard from the people most likely to have one.
     */
    public static final FeatureChecker METHOD_TRACING_DASHBOARD =
            new SamplesFeatureChecker(
                    FeatureType.METHOD_TRACING_DASHBOARD,
                    List.of(Type.METHOD_TRACE, Type.METHOD_TIMING));

    public static final FeatureChecker ASYNC_PROFILER_SPANS =
            new SamplesFeatureChecker(FeatureType.ASYNC_PROFILER_SPANS, Type.SPAN);
}
