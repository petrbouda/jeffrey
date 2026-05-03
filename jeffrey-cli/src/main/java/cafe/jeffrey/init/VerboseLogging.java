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

package cafe.jeffrey.init;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Programmatically flips the Logback root logger to DEBUG. Triggered by the
 * {@code -v}/{@code --verbose} CLI flag, the {@code jeffrey-cli-verbose = true}
 * HOCON field, and the {@code JEFFREY_CLI_VERBOSE} env var so users can enable
 * verbose logging without dropping a custom logback.xml on the classpath.
 */
public final class VerboseLogging {

    private static final Logger LOG = LoggerFactory.getLogger(VerboseLogging.class);

    private VerboseLogging() {
    }

    public static void enable() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        ctx.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.DEBUG);
        LOG.debug("Verbose logging enabled");
    }
}
