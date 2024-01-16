package pbouda.jeffrey.info;

import java.io.Closeable;

public interface StringDatabase extends Closeable {

    void initialize();

    String readContent();

    void writeContent(String content);

    @Override
    default void close() {
    }
}
