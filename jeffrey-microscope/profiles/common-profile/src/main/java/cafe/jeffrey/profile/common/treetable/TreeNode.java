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
