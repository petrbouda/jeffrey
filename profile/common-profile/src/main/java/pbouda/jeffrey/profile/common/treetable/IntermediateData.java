package pbouda.jeffrey.profile.common.treetable;

import java.util.List;

public record IntermediateData(List<String> categories, String name) implements TreeData {

    @Override
    public boolean isLeaf() {
        return false;
    }
}
