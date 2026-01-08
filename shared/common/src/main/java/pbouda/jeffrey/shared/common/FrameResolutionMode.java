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

package pbouda.jeffrey.shared.common;

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
