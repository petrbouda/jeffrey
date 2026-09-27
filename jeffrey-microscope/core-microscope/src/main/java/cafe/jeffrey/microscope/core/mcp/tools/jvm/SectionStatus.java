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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

/**
 * Whether a {@code jvm_} section has a dashboard to show for this recording.
 */
public enum SectionStatus {

    /** The recording carries the section's events, and the dashboard is rendered from them. */
    OK,

    /**
     * The recording carries none of the events the section is built from — the profiler was not
     * configured to capture them — so there is no dashboard, rather than a page of zeroes.
     */
    NOT_RECORDED
}
