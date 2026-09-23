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

package cafe.jeffrey.microscope.model;

/**
 * Frame resolution mode for flamegraph generation.
 * Controls how stack frames are resolved from frame hashes.
 */
public enum FrameResolutionMode {
    /**
     * Optimized Java-side frame resolution (~10x faster).
     * Loads all frames into an in-memory cache and resolves frames using HashMap lookups.
     * Frames are loaded fresh for each flamegraph generation.
     */
    CACHE,

    /**
     * Standard SQL-side frame resolution.
     * Resolves frames directly in the database using JOINs and MAP operations.
     * This is the original implementation.
     */
    DATABASE
}
