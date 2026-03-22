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
 * Represents a reference chain from a GC root to a target object.
 *
 * @param rootObjectId  unique ID of the GC root object
 * @param rootClassName class name of the GC root object
 * @param rootType      type of GC root (e.g. "Java Frame", "JNI Global")
 * @param threadName    thread name for Java Frame roots, null otherwise
 * @param stackFrame    stack frame info (e.g. "MyClass.method(MyClass.java:42)") for Java Frame roots, null otherwise
 * @param steps         ordered chain of references from root to target
 */
public record GCRootPath(
        long rootObjectId,
        String rootClassName,
        String rootType,
        String threadName,
        String stackFrame,
        List<PathStep> steps
) {
}
