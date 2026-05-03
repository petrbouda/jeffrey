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
 * Programmatically flips the Logback root logger to DEBUG. Used by the
 * {@code -v}/{@code --debug} CLI flag, the {@code debug = true} HOCON field,
 * and the {@code JEFFREY_DEBUG} env var so users can enable verbose logging
 * without dropping a custom logback.xml on the classpath.
 */
public final class DebugLogging {

    private static final Logger LOG = LoggerFactory.getLogger(DebugLogging.class);

    private DebugLogging() {
    }

    public static void enable() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        ctx.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.DEBUG);
        LOG.debug("Debug logging enabled");
    }
}
