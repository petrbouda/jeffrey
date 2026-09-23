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

/**
 * Represents a node in the instance tree for exploring object references.
 *
 * @param objectId         the unique identifier of the instance in the heap
 * @param className        the fully qualified class name of the instance
 * @param value            formatted display value of the instance
 * @param shallowSize      shallow size of the instance in bytes
 * @param retainedSize     retained heap size in bytes (null if not calculated)
 * @param fieldName        the field name by which this instance is referenced (null for root)
 * @param relationshipType the type of relationship ("REFERRER", "REACHABLE", or "ROOT")
 * @param hasChildren      indicates whether this node can be expanded
 * @param childCount       approximate number of children (-1 if unknown)
 */
public record InstanceTreeNode(
        long objectId,
        String className,
        String value,
        long shallowSize,
        Long retainedSize,
        String fieldName,
        String relationshipType,
        boolean hasChildren,
        int childCount
) {
    /**
     * Create a root node for the tree.
     */
    public static InstanceTreeNode root(long objectId, String className, String value, long shallowSize, boolean hasChildren, int childCount) {
        return new InstanceTreeNode(objectId, className, value, shallowSize, null, null, "ROOT", hasChildren, childCount);
    }

    /**
     * Create a referrer node (an object that references the parent).
     */
    public static InstanceTreeNode referrer(long objectId, String className, String value, long shallowSize, String fieldName, boolean hasChildren, int childCount) {
        return new InstanceTreeNode(objectId, className, value, shallowSize, null, fieldName, "REFERRER", hasChildren, childCount);
    }

    /**
     * Create a reachable node (an object referenced by the parent).
     */
    public static InstanceTreeNode reachable(long objectId, String className, String value, long shallowSize, String fieldName, boolean hasChildren, int childCount) {
        return new InstanceTreeNode(objectId, className, value, shallowSize, null, fieldName, "REACHABLE", hasChildren, childCount);
    }
}
