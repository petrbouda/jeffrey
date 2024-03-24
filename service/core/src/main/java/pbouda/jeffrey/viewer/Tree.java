package pbouda.jeffrey.viewer;

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

    public void add(List<String> parentNodeNames, String leafNodeName, String code, long eventTypeCount) {
        TreeNode parentNode = createParentPath(root, parentNodeNames, 0);
        if (parentNode.findChild(leafNodeName).isEmpty()) {
            String leafNodeKey = createNodeKey(parentNode.getKey(), parentNode.getChildren().size());
            TreeNode leafNode = new TreeNode(leafNodeKey, new TreeData(leafNodeName, code, eventTypeCount));
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
                currNode = new TreeNode(createNodeKey(parent.getKey(), parent.getChildren().size()), new TreeData(currPath));
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
