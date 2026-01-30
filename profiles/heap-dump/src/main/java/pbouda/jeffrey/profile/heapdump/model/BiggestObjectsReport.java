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

import java.util.List;

/**
 * Report containing the biggest individual objects in the heap by retained size.
 *
 * @param totalHeapSize     total heap size in bytes
 * @param totalRetainedSize total retained size of the reported objects in bytes
 * @param entries           list of biggest object entries ordered by retained size descending
 */
public record BiggestObjectsReport(
        long totalHeapSize,
        long totalRetainedSize,
        List<BiggestObjectEntry> entries
) {
}
