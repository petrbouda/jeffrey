package pbouda.jeffrey.generator.flamegraph;

public enum FrameType {
    C1_COMPILED("C1 compiled", "JAVA C1-compiled", "#cce880"),
    NATIVE("Native", "Native", "#e15a5a"),
    CPP("C++", "C++ (JVM)", "#c8c83c"),
    INTERPRETED("Interpreted", "Interpreted (JAVA)", "#b2e1b2"),
    JIT_COMPILED("JIT compiled", "JIT-compiled (JAVA)", "#50e150"),
    INLINED("Inlined", "Inlined (JAVA)", "#50cccc"),
    KERNEL("Kernel", "#e17d00"),
    THREAD_NAME_SYNTHETIC("Thread Name (Synthetic)", "#e17e5a"),
    ALLOCATED_OBJECT_SYNTHETIC("Allocated Object (Synthetic)", "#00b6ff"),
    ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC("Allocated Object in New TLAB (Synthetic)", "#00b6ff"),
    ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC("Allocated Object Outside TLAB (Synthetic)", "#0031e1"),
    BLOCKING_OBJECT_SYNTHETIC("Blocking Object (Synthetic)", "#e17e5a"),
    UNKNOWN("Unknown", "#000000");

    private static final FrameType[] VALUES = values();

    private final String code;
    private final String title;
    private final String color;

    FrameType(String title, String color) {
        this(null, title, color);
    }

    FrameType(String code, String title, String color) {
        this.code = code;
        this.title = title;
        this.color = color;
    }

    public static FrameType fromCode(String code) {
        for (FrameType value : VALUES) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new RuntimeException("Frame type does not exists: " + code);
    }

    public String color() {
        return color;
    }

    public String title() {
        return title;
    }
}
