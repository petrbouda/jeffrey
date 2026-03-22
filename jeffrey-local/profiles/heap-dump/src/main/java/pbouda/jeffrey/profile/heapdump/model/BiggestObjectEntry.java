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

/**
 * A single entry in the biggest objects report representing one large object in the heap.
 *
 * @param className    fully qualified class name of the object
 * @param shallowSize  shallow size in bytes
 * @param retainedSize retained size in bytes
 * @param objectId     object ID in the heap dump
 */
public record BiggestObjectEntry(
        String className,
        long shallowSize,
        long retainedSize,
        long objectId
) {
}
