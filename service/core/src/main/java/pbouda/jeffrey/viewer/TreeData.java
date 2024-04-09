package pbouda.jeffrey.viewer;

public record TreeData(String name, String code, long count, boolean withStackTrace) {

    public TreeData(String name) {
        this(name, null, 0, false);
    }
}
