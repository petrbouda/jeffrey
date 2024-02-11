package pbouda.jeffrey.graph.diff;

import one.Frame;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

public class DiffTreeGenerator {

    private final Frame baseline;
    private final Frame comparison;

    public DiffTreeGenerator(Frame baseline, Frame comparison) {
        this.baseline = baseline;
        this.comparison = comparison;
    }

    public DiffFrame generate() {
        DiffFrame artificialNode = DiffFrame.shared("-", Byte.MIN_VALUE, -1, -1);
        walkTree(artificialNode, "all", baseline, comparison);
        return artificialNode.get("all");
    }

    private void walkTree(TreeMap<String, DiffFrame> diffFrame, String currentMethodName, Frame bFrame, Frame cFrame) {
        if (bFrame == null) {
            diffFrame.put(currentMethodName, DiffFrame.added(cFrame, currentMethodName));
        } else if (cFrame == null) {
            diffFrame.put(currentMethodName, DiffFrame.removed(bFrame, currentMethodName));
        } else {
            DiffFrame newFrame = DiffFrame.shared(currentMethodName, bFrame.type, bFrame.samples, cFrame.samples);
            diffFrame.put(currentMethodName, newFrame);

            Set<String> nextLayer = new HashSet<>();
            nextLayer.addAll(bFrame.keySet());
            nextLayer.addAll(cFrame.keySet());

            for (String methodName : nextLayer) {
                Frame newBFrame = bFrame.get(methodName);
                Frame newCFrame = cFrame.get(methodName);
                walkTree(newFrame, methodName, newBFrame, newCFrame);
            }
        }
    }
}
