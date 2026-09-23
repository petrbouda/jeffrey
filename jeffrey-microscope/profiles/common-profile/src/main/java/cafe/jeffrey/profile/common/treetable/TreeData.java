/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
