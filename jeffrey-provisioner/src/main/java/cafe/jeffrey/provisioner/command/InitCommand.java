/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import java.time.Clock;
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
            new InitExecutor(Clock.systemUTC()).execute(config);
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
