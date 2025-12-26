package pbouda.jeffrey.profile.common.treetable;

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
