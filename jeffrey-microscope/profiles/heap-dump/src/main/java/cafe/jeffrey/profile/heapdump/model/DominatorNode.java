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

package cafe.jeffrey.profile.heapdump.model;

import java.util.List;
import java.util.Map;

/**
 * A node in the dominator/retained-size tree.
 *
 * @param objectId        unique object identifier
 * @param className       fully qualified class name
 * @param objectParams    structured key/value pairs describing the object
 * @param fieldName       field name referencing this object (null for root nodes)
 * @param shallowSize     shallow size of this object in bytes
 * @param retainedSize    retained size of this object in bytes
 * @param retainedPercent percentage of parent's retained size this node occupies
 * @param hasChildren     whether this node has expandable children
 * @param gcRootKind      GC root kind (e.g. "Java Frame", "Thread Obj") or null if not a GC root
 * @param referrerClasses distinct class names of objects pointing at this instance, ordered by
 *                        reference count descending; populated only for opaque primitive-array
 *                        rows (e.g. {@code byte[]}) to surface who holds the bytes; empty list
 *                        otherwise
 */
public record DominatorNode(
        long objectId,
        String className,
        Map<String, String> objectParams,
        String fieldName,
        long shallowSize,
        long retainedSize,
        double retainedPercent,
        boolean hasChildren,
        String gcRootKind,
        List<String> referrerClasses
) {
}
