package pbouda.jeffrey.scheduler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GeneratingTask implements Runnable {

    private final Path jfrDir;
    private final Path repositoryDir;

    public GeneratingTask(Path jfrDir, Path repositoryDir) {
        this.jfrDir = jfrDir;
        this.repositoryDir = repositoryDir;
    }

    @Override
    public void run() {
        try {
            Set<Path> repositories = Files.list(repositoryDir)
                    .collect(Collectors.toSet());

            // All unprocessed JFR files
            List<Path> jfrs = Files.list(jfrDir)
                    .filter(path -> !repositories.contains(path))
                    .toList();


        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
