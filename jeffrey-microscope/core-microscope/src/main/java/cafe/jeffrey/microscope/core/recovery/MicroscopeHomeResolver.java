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

package cafe.jeffrey.microscope.core.recovery;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Resolves the Jeffrey home directory, the external database URL and the HTTP port before the
 * Spring context exists, mirroring Spring's property precedence: command-line argument &gt;
 * system property &gt; environment variable &gt; default.
 */
public final class MicroscopeHomeResolver {

    public static final String HOME_DIR_PROPERTY = "jeffrey.microscope.home.dir";
    private static final String HOME_DIR_ENV = "JEFFREY_MICROSCOPE_HOME_DIR";
    private static final String DATABASE_URL_PROPERTY = "jeffrey.microscope.persistence.database.url";
    private static final String DATABASE_URL_ENV = "JEFFREY_MICROSCOPE_PERSISTENCE_DATABASE_URL";
    private static final String SERVER_PORT_PROPERTY = "server.port";
    private static final String SERVER_PORT_ENV = "SERVER_PORT";
    private static final String USER_HOME_PROPERTY = "user.home";
    private static final String DEFAULT_HOME_DIR_NAME = ".jeffrey-microscope";
    private static final String CLI_ARG_PREFIX = "--";
    private static final String CLI_ARG_SEPARATOR = "=";
    private static final int DEFAULT_PORT = 8080;

    private final Map<String, String> environment;
    private final Properties systemProperties;

    public MicroscopeHomeResolver(Map<String, String> environment, Properties systemProperties) {
        this.environment = environment;
        this.systemProperties = systemProperties;
    }

    public static MicroscopeHomeResolver fromSystem() {
        return new MicroscopeHomeResolver(System.getenv(), System.getProperties());
    }

    public ResolvedBootConfig resolve(String[] args) {
        String homeDir = resolveValue(args, HOME_DIR_PROPERTY, HOME_DIR_ENV);
        if (isBlank(homeDir)) {
            homeDir = systemProperties.getProperty(USER_HOME_PROPERTY) + "/" + DEFAULT_HOME_DIR_NAME;
        }

        String databaseUrl = resolveValue(args, DATABASE_URL_PROPERTY, DATABASE_URL_ENV);
        int port = parsePort(resolveValue(args, SERVER_PORT_PROPERTY, SERVER_PORT_ENV));

        return new ResolvedBootConfig(
                Path.of(homeDir).toAbsolutePath().normalize(),
                Optional.ofNullable(isBlank(databaseUrl) ? null : databaseUrl),
                port);
    }

    /**
     * Returns the args with the home-dir argument pinned to the given directory. A command-line
     * argument is Spring's highest-precedence property source, so the returned array resolves to
     * the same home directory this pre-flight decided on, regardless of how the original value
     * was supplied.
     */
    public static String[] withHomeDir(String[] args, Path homeDir) {
        String argPrefix = CLI_ARG_PREFIX + HOME_DIR_PROPERTY + CLI_ARG_SEPARATOR;
        String[] rewritten = Arrays.stream(args)
                .filter(arg -> !arg.startsWith(argPrefix))
                .toArray(String[]::new);

        String[] result = Arrays.copyOf(rewritten, rewritten.length + 1);
        result[rewritten.length] = argPrefix + homeDir;
        return result;
    }

    private String resolveValue(String[] args, String property, String envName) {
        String cliValue = cliArgument(args, property);
        if (cliValue != null) {
            return cliValue;
        }
        String propertyValue = systemProperties.getProperty(property);
        if (propertyValue != null) {
            return propertyValue;
        }
        return environment.get(envName);
    }

    private static String cliArgument(String[] args, String property) {
        String argPrefix = CLI_ARG_PREFIX + property + CLI_ARG_SEPARATOR;
        for (String arg : args) {
            if (arg.startsWith(argPrefix)) {
                return arg.substring(argPrefix.length());
            }
        }
        return null;
    }

    private static int parsePort(String value) {
        if (isBlank(value)) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
