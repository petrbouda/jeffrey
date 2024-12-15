import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class InitContainerScript {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Repository folder is not provided");
        }
        if (args.length > 1) {
            System.out.println("Too many arguments, only one is expected");
        }

        Path repositoryFolder = Path.of(args[0]);
        createDirectories(repositoryFolder);
        if (!repositoryFolder.toFile().exists()) {
            System.out.println("Cannot create parent directories: " + repositoryFolder);
        }
    }

    private static void createDirectories(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            System.out.println("Cannot create a parent directories: " + path);
        }
    }
}
