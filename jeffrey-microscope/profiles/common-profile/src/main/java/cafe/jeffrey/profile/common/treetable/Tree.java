/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
import java.util.Optional;

public class Tree {

    private final TreeNode root;

    public Tree() {
        this(new TreeNode(null, null));
    }

    public Tree(TreeNode root) {
        this.root = root;
    }

    public void add(TreeData data) {
        TreeNode parentNode = createParentPath(root, data.categories(), 0);
        if (parentNode.findChild(data.name()).isEmpty()) {
            String leafNodeKey = createNodeKey(parentNode.getKey(), parentNode.getChildren().size());
            TreeNode leafNode = new TreeNode(leafNodeKey, data);
            parentNode.addChild(leafNode);
        }
    }

    private TreeNode createParentPath(TreeNode parent, List<String> parentNodeNames, int layer) {
        if (parentNodeNames.size() == layer) {
            return parent;
        } else {
            String currPath = parentNodeNames.get(layer);
            Optional<TreeNode> child = parent.findChild(currPath);

            TreeNode currNode;
            if (child.isEmpty() || child.get().isLeaf()) {
                String nodeKey = createNodeKey(parent.getKey(), parent.getChildren().size());
                currNode = new TreeNode(nodeKey, new IntermediateData(parentNodeNames, currPath));
                parent.addChild(currNode);
            } else {
                currNode = child.get();
            }

            return createParentPath(currNode, parentNodeNames, layer + 1);
        }
    }

    private String createNodeKey(String parentKey, int siblings) {
        if (parentKey != null) {
            return parentKey + "-" + siblings;
        } else {
            return String.valueOf(siblings);
        }
    }

    public TreeNode getRoot() {
        return root;
    }
}
