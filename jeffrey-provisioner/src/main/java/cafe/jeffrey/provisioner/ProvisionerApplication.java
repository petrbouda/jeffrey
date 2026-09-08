/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.provisioner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.provisioner.command.InitCommand;
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
        description = "Jeffrey Provisioner to simplify the setup and maintenance",
        versionProvider = VersionProvider.class
)
public class ProvisionerApplication {

    private static final Logger LOG = LoggerFactory.getLogger(ProvisionerApplication.class);

    @Option(
            names = {"-v", "--verbose"},
            scope = ScopeType.INHERIT,
            description = "Enable DEBUG-level logging across all loggers (overrides logback.xml root level)."
    )
    private boolean verbose;

    static void main(String... args) {
        ProvisionerApplication app = new ProvisionerApplication();
        CommandLine cmd = new CommandLine(app)
                .setUsageHelpWidth(160)
                .setExecutionStrategy(parseResult -> {
                    if (app.verbose) {
                        VerboseLogging.enable();
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
}
