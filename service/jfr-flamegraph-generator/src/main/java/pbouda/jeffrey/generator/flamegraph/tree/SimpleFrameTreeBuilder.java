package pbouda.jeffrey.generator.flamegraph.tree;

import pbouda.jeffrey.generator.flamegraph.record.StackBasedRecord;

public class SimpleFrameTreeBuilder extends FrameTreeBuilder<StackBasedRecord> {

    public SimpleFrameTreeBuilder(boolean threadMode) {
        super(false, threadMode);
    }
}
