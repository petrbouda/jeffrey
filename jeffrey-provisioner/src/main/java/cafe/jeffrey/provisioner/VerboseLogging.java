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

package cafe.jeffrey.provisioner;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Programmatically flips the Logback root logger to DEBUG. Triggered by the
 * {@code -v}/{@code --verbose} command-line flag, the {@code provisioner-verbose = true}
 * HOCON field, and the {@code JEFFREY_PROVISIONER_VERBOSE} env var so users can enable
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
