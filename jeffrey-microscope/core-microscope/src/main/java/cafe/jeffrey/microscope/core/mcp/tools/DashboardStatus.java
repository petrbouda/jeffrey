/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.microscope.core.mcp.tools;

/**
 * Whether a technology dashboard ({@code http_}, {@code jdbc_}, {@code grpc_}, {@code io_},
 * {@code blocking_}, {@code memory_}, {@code methodtracing_}) has figures to show for this recording.
 */
public enum DashboardStatus {

    /** The recording carries the events the dashboard is built from, and the figures are below. */
    OK,

    /**
     * The recording carries none of the events the dashboard is built from - the profiler was not
     * configured for them, or they are threshold-gated and nothing crossed the threshold - so there is
     * no dashboard, rather than a page of zeroes that would read as a measurement.
     */
    NOT_RECORDED
}
