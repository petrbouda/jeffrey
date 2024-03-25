package pbouda.jeffrey.viewer;

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
        return data.code() != null;
    }
}
