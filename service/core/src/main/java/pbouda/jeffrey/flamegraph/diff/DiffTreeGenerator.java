package pbouda.jeffrey.flamegraph.diff;

import one.Frame;

import java.util.*;

public class DiffTreeGenerator {

    private final Frame baseline;
    private final Frame comparison;

    public DiffTreeGenerator(Frame baseline, Frame comparison) {
        this.baseline = baseline;
        this.comparison = comparison;
    }

    public DiffTree generate() {
        DiffFrame artificialNode = DiffFrame.partial("-", Byte.MIN_VALUE, -1, -1);
        walkTree(artificialNode, "all", baseline, comparison);
        return new DiffTree(artificialNode.get("all"), baseline.total, comparison.total);
    }

    private void walkTree(TreeMap<String, DiffFrame> diffFrame, String currentMethodName, Frame bFrame, Frame cFrame) {
        if (bFrame == null) {
            diffFrame.put(currentMethodName, DiffFrame.added(cFrame));
        } else if (cFrame == null) {
            diffFrame.put(currentMethodName, DiffFrame.removed(bFrame));
        } else {
            DiffFrame newFrame = DiffFrame.partial(currentMethodName, bFrame.type, bFrame.total, cFrame.total);
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
