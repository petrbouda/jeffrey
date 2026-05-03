package cafe.jeffrey.init;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.init.command.InitCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;

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

    @Option(
            names = {"-v", "--debug"},
            scope = ScopeType.INHERIT,
            description = "Enable DEBUG-level logging across all loggers (overrides logback.xml root level)."
    )
    private boolean debug;

    static void main(String... args) {
        CliApplication app = new CliApplication();
        CommandLine cmd = new CommandLine(app)
                .setUsageHelpWidth(160)
                .setExecutionStrategy(parseResult -> {
                    if (app.debug) {
                        enableDebugLogging();
                    }
                    return new CommandLine.RunLast().execute(parseResult);
                });

        int exitCode;
        try {
            exitCode = cmd.execute(args);
        } catch (Exception e) {
            LOG.error("Unexpected error", e);
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    /**
     * Bumps the Logback root logger to DEBUG. Done programmatically so a single
     * {@code --debug} flag flips visibility without users needing to drop a
     * custom logback.xml on the classpath.
     */
    private static void enableDebugLogging() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        ctx.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.DEBUG);
        LOG.debug("Debug logging enabled via --debug");
    }
}
