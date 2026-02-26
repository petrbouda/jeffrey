package pbouda.jeffrey.init.model;

import java.util.Arrays;

public enum HeapDumpType {
    CRASH, EXIT;

    public static HeapDumpType resolve(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Repository type cannot be null");
        }

        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid heap dump type: " + value));
    }
}
