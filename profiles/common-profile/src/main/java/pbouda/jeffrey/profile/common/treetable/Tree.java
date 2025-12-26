/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.profile.common.treetable;

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
