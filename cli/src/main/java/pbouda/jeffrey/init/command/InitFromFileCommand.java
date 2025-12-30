package pbouda.jeffrey.init.command;

import pbouda.jeffrey.init.InitConfig;
import pbouda.jeffrey.init.InitExecutor;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

@Command(
        name = InitFromFileCommand.COMMAND_NAME,
        description = "Initialize Jeffrey project and current session from a HOCON configuration file.",
        mixinStandardHelpOptions = true
)
public class InitFromFileCommand implements Runnable {

    public static final String COMMAND_NAME = "init";

    @Parameters(index = "0", description = "Path to the HOCON configuration file.")
    private Path configFile;

    @Override
    public void run() {
        try {
            InitConfig config = InitConfig.fromHoconFile(configFile);
            new InitExecutor().execute(config);
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
        }
    }
}
