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
 * Whether a {@code flamegraph_export} drew the event type it was asked for.
 */
public enum GraphStatus {

    /** The recording carries samples of the event type, and the Markdown is their call tree. */
    OK,

    /**
     * The recording carries no samples of the event type, so there is no tree to draw — rather than an
     * empty one that would read as "nothing was hot". The answer names the types it did record.
     */
    NOT_RECORDED
}
