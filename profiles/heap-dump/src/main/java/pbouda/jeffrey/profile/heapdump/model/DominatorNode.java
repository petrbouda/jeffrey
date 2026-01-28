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
 * A node in the dominator/retained-size tree.
 *
 * @param objectId        unique object identifier
 * @param className       fully qualified class name
 * @param displayValue    human-readable preview of the object value
 * @param shallowSize     shallow size of this object in bytes
 * @param retainedSize    retained size of this object in bytes
 * @param retainedPercent percentage of parent's retained size this node occupies
 * @param hasChildren     whether this node has expandable children
 */
public record DominatorNode(
        long objectId,
        String className,
        String displayValue,
        long shallowSize,
        long retainedSize,
        double retainedPercent,
        boolean hasChildren
) {
}
