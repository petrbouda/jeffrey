package pbouda.jeffrey.viewer;

public record TreeData(String name, String code, long count) {

    public TreeData(String name) {
        this(name, null, 0);
    }
}
