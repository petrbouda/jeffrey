package pbouda.jeffrey.processor;

import java.nio.file.Path;

public interface JfrProcessor {

    void process(Path path);

    boolean applicable();
}
