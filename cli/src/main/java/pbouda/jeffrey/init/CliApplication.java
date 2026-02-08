package pbouda.jeffrey.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.init.command.InitCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "",
        subcommands = {
                InitCommand.class,
        },
        mixinStandardHelpOptions = true,
        description = "Jeffrey CLI Application to simplify the setup and maintenance",
        versionProvider = VersionProvider.class
)
public class CliApplication {

    private static final Logger LOG = LoggerFactory.getLogger(CliApplication.class);

    static void main(String... args) {
        try {
            new CommandLine(new CliApplication())
                    .setUsageHelpWidth(160)
                    .execute(args);
        } catch (Exception e) {
            LOG.error("Unexpected error: {}", e.getMessage(), e);
        }
        System.exit(0);
    }
}
