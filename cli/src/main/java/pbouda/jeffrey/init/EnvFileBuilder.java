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

/**
 * Builds the content of the .env file with environment variables for Jeffrey sessions.
 */
public class EnvFileBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(EnvFileBuilder.class);

    private static final String DEFAULT_FILE_TEMPLATE = "profile-%t.jfr";
    private static final String JEFFREY_HOME_PROP = "JEFFREY_HOME";
    private static final String JEFFREY_WORKSPACES_PROP = "JEFFREY_WORKSPACES";
    private static final String JEFFREY_WORKSPACE_PROP = "JEFFREY_CURRENT_WORKSPACE";
    private static final String JEFFREY_SESSION_PROP = "JEFFREY_CURRENT_SESSION";
    private static final String JEFFREY_PROJECT_PROP = "JEFFREY_CURRENT_PROJECT";
    private static final String JEFFREY_FILE_PATTERN_PROP = "JEFFREY_FILE_PATTERN";
    private static final String JEFFREY_PROFILER_CONFIG_PROP = "JEFFREY_PROFILER_CONFIG";
    private static final String JDK_JAVA_OPTIONS_PROP = "JDK_JAVA_OPTIONS";

    /**
     * Context containing all the paths and settings needed to build the ENV file.
     */
    public record Context(
            Path jeffreyHome,
            Path workspacesPath,
            Path workspacePath,
            Path projectPath,
            Path sessionPath,
            String profilerSettings,
            boolean useJeffreyHome,
            boolean exportJdkJavaOptions
    ) {}

    /**
     * Builds the content of the .env file.
     *
     * @param context the context containing all required paths and settings
     * @return the content of the .env file with export statements
     */
    public String build(Context context) {
        LOG.debug("Building env file: sessionPath={} useJeffreyHome={}", context.sessionPath(), context.useJeffreyHome());
        StringBuilder output = new StringBuilder();

        if (context.useJeffreyHome()) {
            appendVar(output, JEFFREY_HOME_PROP, context.jeffreyHome());
        }

        appendVar(output, JEFFREY_WORKSPACES_PROP, context.workspacesPath());
        appendVar(output, JEFFREY_WORKSPACE_PROP, context.workspacePath());
        appendVar(output, JEFFREY_PROJECT_PROP, context.projectPath());
        appendVar(output, JEFFREY_SESSION_PROP, context.sessionPath());
        appendVar(output, JEFFREY_FILE_PATTERN_PROP, context.sessionPath().resolve(DEFAULT_FILE_TEMPLATE));

        if (context.profilerSettings() != null && !context.profilerSettings().isEmpty()) {
            appendVar(output, JEFFREY_PROFILER_CONFIG_PROP, wrapQuotes(context.profilerSettings()));
            if (context.exportJdkJavaOptions()) {
                appendVarNoNewline(output, JDK_JAVA_OPTIONS_PROP, wrapQuotes(context.profilerSettings()));
            }
        }

        return output.toString();
    }

    private static void appendVar(StringBuilder sb, String name, Path value) {
        appendVar(sb, name, value.toString());
    }

    private static void appendVar(StringBuilder sb, String name, String value) {
        sb.append("export ").append(name).append("=").append(value).append("\n");
    }

    private static void appendVarNoNewline(StringBuilder sb, String name, String value) {
        sb.append("export ").append(name).append("=").append(value);
    }

    private static String wrapQuotes(String value) {
        return "'" + value + "'";
    }
}
