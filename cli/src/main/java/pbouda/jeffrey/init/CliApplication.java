package pbouda.jeffrey.init;

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

    static void main(String... args) {
        try {
            new CommandLine(new CliApplication())
                    .setUsageHelpWidth(160)
                    .execute(args);
        } catch (Exception e) {
            System.err.println("[ERROR] Unexpected error: " + e.getMessage());
        }
        System.exit(0);
    }
}
