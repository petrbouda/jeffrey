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

enum FeatureType {
  HTTP_SERVER_DASHBOARD = 'HTTP_SERVER_DASHBOARD',
  HTTP_CLIENT_DASHBOARD = 'HTTP_CLIENT_DASHBOARD',
  GRPC_SERVER_DASHBOARD = 'GRPC_SERVER_DASHBOARD',
  GRPC_CLIENT_DASHBOARD = 'GRPC_CLIENT_DASHBOARD',
  JDBC_STATEMENTS_DASHBOARD = 'JDBC_STATEMENTS_DASHBOARD',
  JDBC_POOL_DASHBOARD = 'JDBC_POOL_DASHBOARD',
  CONTAINER_DASHBOARD = 'CONTAINER_DASHBOARD',
  PERF_COUNTERS_DASHBOARD = 'PERF_COUNTERS_DASHBOARD',
  // JDK method timing and tracing (JEP 520), not distributed tracing -- see TRACES.
  METHOD_TRACING_DASHBOARD = 'METHOD_TRACING_DASHBOARD',
  TRACES = 'TRACES',
  ASYNC_PROFILER_SPANS = 'ASYNC_PROFILER_SPANS',
  HEAP_DUMP = 'HEAP_DUMP',
  SUBSECOND = 'SUBSECOND',
  TIMESERIES = 'TIMESERIES'
}

export default FeatureType;
