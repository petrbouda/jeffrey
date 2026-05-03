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

package cafe.jeffrey.init.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.init.DebugLogging;
import cafe.jeffrey.init.InitConfig;
import cafe.jeffrey.init.InitExecutor;
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
            if (config.isDebug()) {
                DebugLogging.enable();
            }
            new InitExecutor().execute(config);
        } catch (Exception e) {
            LOG.error("Init failed", e);
        }
    }
}
