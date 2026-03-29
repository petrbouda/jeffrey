/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
 * A local variable or reference on a thread's stack frame.
 *
 * @param objectId    the object ID of the referenced instance
 * @param className   the class name of the referenced instance
 * @param fieldName   the name of the local variable (if available)
 * @param shallowSize the shallow size of the referenced instance in bytes
 */
public record StackFrameLocal(
        long objectId,
        String className,
        String fieldName,
        long shallowSize
) {
}
