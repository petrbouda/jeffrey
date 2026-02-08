/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class CliApplication {

    private static final Logger LOG = LoggerFactory.getLogger(CliApplication.class);

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: jeffrey-cli <config-file>");
            System.exit(1);
        }
        try {
            Path configFile = Path.of(args[0]);
            InitConfig config = InitConfig.fromHoconFile(configFile);
            new InitExecutor().execute(config);
        } catch (Exception e) {
            LOG.error("Failed to initialize: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
