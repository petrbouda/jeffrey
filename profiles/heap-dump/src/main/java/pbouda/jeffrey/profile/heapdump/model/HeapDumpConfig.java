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

package pbouda.jeffrey.profile.heapdump.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Persisted configuration resolved during heap dump initialization.
 *
 * @param compressedOops       whether compressed oops are enabled
 * @param compressedOopsSource how compressed oops was determined: MANUAL, JFR, or INFERRED
 * @param totalOvercount       heap-wide reference size overcount (only meaningful when compressedOops is true)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HeapDumpConfig(
        boolean compressedOops,
        String compressedOopsSource,
        long totalOvercount
) {
}
