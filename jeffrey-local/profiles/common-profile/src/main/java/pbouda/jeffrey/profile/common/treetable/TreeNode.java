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

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * <a href="https://primevue.org/treetable/#api.treenode">PrimeVue UI TreeNode</a>
 */
public class TreeNode {
    private final String key;
    private final TreeData data;
    private final List<TreeNode> children;

    public TreeNode(String key, TreeData data) {
        this.key = key;
        this.data = data;
        this.children = new LinkedList<>();
    }

    public void addChild(TreeNode childNode) {
        this.children.add(childNode);
    }

    public TreeData getData() {
        return data;
    }

    public String getKey() {
        return key;
    }


    public List<TreeNode> getChildren() {
        return children;
    }

    public Optional<TreeNode> findChild(String name) {
        for (TreeNode child : children) {
            if (child.getData().name().equals(name)) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    public boolean isLeaf() {
        return data.isLeaf();
    }
}
