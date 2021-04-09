package pbouda.jeffrey;

import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class Directory {

    private static final Logger LOG = LoggerFactory.getLogger(Directory.class);

    public static void init(Config filesystem) {
        filesystem.entrySet().stream()
                .map(entry -> entry.getValue().render())
                .map(Path::of)
                .filter(dir -> !Files.exists(dir))
                .forEach(dir -> {
                    try {
                        LOG.info("STRUCTURE_INIT: Create a missing directory: " + dir);
                        Files.createDirectories(dir);
                    } catch (IOException e) {
                        throw new RuntimeException("Cannot init a JFR_PLOTS directory structure: " + dir, e);
                    }
                });
    }
}
