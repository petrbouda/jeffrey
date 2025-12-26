package pbouda.jeffrey.init.command;

import pbouda.jeffrey.init.InitExecutor;
import pbouda.jeffrey.init.InitOptions;
import pbouda.jeffrey.init.InitOptionsBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

@Command(
        name = InitFromFileCommand.COMMAND_NAME,
        description = "Initialize Jeffrey project and current session from a HOCON configuration file.",
        mixinStandardHelpOptions = true
)
public class InitFromFileCommand implements Runnable {

    public static final String COMMAND_NAME = "init-from-file";

    @Parameters(index = "0", description = "Path to the HOCON configuration file.")
    private Path configFile;

    @Override
    public void run() {
        try {
            InitOptions options = InitOptionsBuilder.fromHoconFile(configFile).build();
            new InitExecutor().execute(options);
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
        }
    }
}
