package pbouda.jeffrey.processor;

import java.nio.file.Path;

public class JfrProcessingTask implements Runnable {

    private final Path path;

    public JfrProcessingTask(Path path) {
        this.path = path;
    }

    @Override
    public void run() {

    }
}
