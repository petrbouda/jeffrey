package pbouda.jeffrey.graph;

public enum FrameType {
    C1_COMPILED("C1 compiled", "#cce880"),
    NATIVE("Native", "#e15a5a"),
    CPP("C++", "#c8c83c"),
    INTERPRETED("Interpreted", "#b2e1b2"),
    JIT_COMPILED("JIT compiled", "#50e150"),
    INLINED("Inlined", "#50cccc"),
    KERNEL("Kernel", "#e17d00"),
    UNKNOWN("Unknown", "#000000");

    private static final FrameType[] VALUES = values();

    private final String code;
    private final String color;

    FrameType(String code, String color) {
        this.code = code;
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
}
