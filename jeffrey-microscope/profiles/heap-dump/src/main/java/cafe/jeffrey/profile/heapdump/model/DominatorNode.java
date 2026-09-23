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
