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

package pbouda.jeffrey.profile.parser.chunk;

/**
 * Constants for JFR chunk parsing.
 */
public interface JfrChunkConstants {

    /**
     * Size of the JFR chunk header in bytes.
     */
    int CHUNK_HEADER_SIZE = 68;

    /**
     * Magic number identifying a JFR chunk ("FLR\0").
     */
    int CHUNK_MAGIC = 0x464c5200;

    /**
     * Feature flag mask indicating this is the final chunk in a recording.
     */
    int MASK_FINAL_CHUNK = 1 << 1;
}
