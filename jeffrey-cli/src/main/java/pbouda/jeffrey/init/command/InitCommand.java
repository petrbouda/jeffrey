package pbouda.jeffrey.init.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.init.InitConfig;
import pbouda.jeffrey.init.InitExecutor;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

@Command(
        name = InitCommand.COMMAND_NAME,
        description = "Initialize Jeffrey project and current session from a HOCON configuration file.",
        mixinStandardHelpOptions = true
)
public class InitCommand implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(InitCommand.class);

    public static final String COMMAND_NAME = "init";

    @Option(names = "--base-config", required = true, description = "Path to the base HOCON configuration file.")
    private Path baseConfigFile;

    @Option(names = "--override-config", description = "Path to an override HOCON configuration file.")
    private Path overrideConfigFile;

    @Override
    public void run() {
        try {
            InitConfig config = InitConfig.fromHoconFile(baseConfigFile, overrideConfigFile);
            new InitExecutor().execute(config);
        } catch (Exception e) {
            LOG.error(e.getMessage());
        }
    }
}
