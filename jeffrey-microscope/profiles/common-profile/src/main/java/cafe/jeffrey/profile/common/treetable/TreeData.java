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

package cafe.jeffrey.profile.common.treetable;

import java.util.List;

public sealed interface TreeData permits EventViewerData, RecordingData, IntermediateData {

    /**
     * All categories in a row where the data belongs to.
     *
     * @return a list of categories.
     */
    List<String> categories();

    /**
     * A name of the node. It's used for comparing the two nodes and building the path.
     *
     * @return a name of the node.
     */
    String name();

    /**
     * Determines whether the node is a leaf, it means it contains a data, or it's intermediate
     * node == just a folder without holding a data.
     *
     * @return {@code true} if the node is holding a data and is not an intermediate folder.
     */
    boolean isLeaf();

}
