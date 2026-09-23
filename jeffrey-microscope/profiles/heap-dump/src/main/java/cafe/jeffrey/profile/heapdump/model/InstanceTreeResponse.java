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

/**
 * Response containing instance tree data for navigating object references.
 *
 * @param root       the root node of the tree (the instance being explored)
 * @param children   list of child nodes (referrers or reachables)
 * @param hasMore    indicates if there are more children available
 * @param totalCount total number of children (may be approximate)
 */
public record InstanceTreeResponse(
        InstanceTreeNode root,
        List<InstanceTreeNode> children,
        boolean hasMore,
        int totalCount
) {
    /**
     * Create an empty response for when the instance is not found.
     */
    public static InstanceTreeResponse notFound() {
        return new InstanceTreeResponse(null, List.of(), false, 0);
    }

    /**
     * Create a response with the given data.
     */
    public static InstanceTreeResponse of(InstanceTreeNode root, List<InstanceTreeNode> children, boolean hasMore, int totalCount) {
        return new InstanceTreeResponse(root, children, hasMore, totalCount);
    }
}
