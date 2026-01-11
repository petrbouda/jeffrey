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
