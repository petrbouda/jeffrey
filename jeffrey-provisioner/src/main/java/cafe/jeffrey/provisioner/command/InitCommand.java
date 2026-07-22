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

package cafe.jeffrey.provisioner.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.provisioner.InitConfig;
import cafe.jeffrey.provisioner.InitExecutor;
import cafe.jeffrey.provisioner.VerboseLogging;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = InitCommand.COMMAND_NAME,
        description = "Initialize Jeffrey project and current session from a HOCON configuration file "
                + "or, when no file is given, from JEFFREY_* environment variables.",
        mixinStandardHelpOptions = true
)
public class InitCommand implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(InitCommand.class);

    public static final String COMMAND_NAME = "init";

    @Option(names = "--base-config", description = "Path to the base HOCON configuration file. "
            + "Optional — without it, configuration is read from JEFFREY_* environment variables.")
    private Path baseConfigFile;

    @Option(names = "--override-config", description = "Path to an override HOCON configuration file.")
    private Path overrideConfigFile;

    /**
     * Returns a non-zero exit code on any failure so callers (the jeffrey-jib entrypoint
     * wrapper) can distinguish "provisioned" from "failed" and start the application
     * without profiling instead of pointing the JVM at an argfile that was never written.
     */
    @Override
    public Integer call() {
        try {
            InitConfig config = resolveConfig();
            if (config.isProvisionerVerbose()) {
                VerboseLogging.enable();
            }
            new InitExecutor().execute(config);
            return ExitCode.OK;
        } catch (Exception e) {
            LOG.error("Init failed", e);
            return ExitCode.SOFTWARE;
        }
    }

    private InitConfig resolveConfig() {
        if (baseConfigFile != null) {
            return InitConfig.fromHoconFile(baseConfigFile, overrideConfigFile);
        }
        LOG.info("No --base-config given, configuring from JEFFREY_* environment variables");
        return InitConfig.fromEnvironment();
    }
}
