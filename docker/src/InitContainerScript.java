import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class InitContainerScript {
    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    private static final String DEFAULT_FILE_TEMPLATE = "profile-%t.jfr";
    private static final String ENV_FILE_NAME = ".env";


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

        Path newFolder = createNewFolder(repositoryFolder);

        createEnvFile(repositoryFolder, newFolder);
    }

    private static Path createNewFolder(Path repositoryFolder) {
        Instant currenTimestamp = Instant.now();
        String folderName = currenTimestamp.atZone(ZoneOffset.UTC).format(DATETIME_FORMATTER);
        return repositoryFolder.resolve(folderName);
    }

    private static void createEnvFile(Path repositoryFolder, Path newFolder) {
        String content = """
                JEFFREY_REPOSITORY_DIR=%s
                JEFFREY_PROFILE_DIR=%s
                JEFFREY_PROFILE_FILE=%s
                """.formatted(
                repositoryFolder,
                newFolder,
                newFolder.resolve(DEFAULT_FILE_TEMPLATE));

        Path envFilePath = repositoryFolder.resolve(ENV_FILE_NAME);
        try {
            Files.writeString(envFilePath, content);
        } catch (IOException e) {
            System.out.println("Cannot create an ENV file: " + envFilePath);
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
