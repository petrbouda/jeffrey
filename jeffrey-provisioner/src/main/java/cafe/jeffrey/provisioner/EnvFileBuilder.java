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

package cafe.jeffrey.provisioner;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the content of the .env file with environment variables for Jeffrey sessions.
 */
public class EnvFileBuilder {

    /**
     * Also the source of {@code <<JEFFREY:FILE_PATTERN>>}, so the placeholder and the exported
     * {@code JEFFREY_FILE_PATTERN} can never drift apart.
     */
    public static final String DEFAULT_FILE_TEMPLATE = "profile-%t.jfr";
    private static final String JEFFREY_HOME_PROP = "JEFFREY_HOME";
    private static final String JEFFREY_WORKSPACES_PROP = "JEFFREY_WORKSPACES";
    private static final String JEFFREY_WORKSPACE_PROP = "JEFFREY_CURRENT_WORKSPACE";
    private static final String JEFFREY_SESSION_PROP = "JEFFREY_CURRENT_SESSION";
    private static final String JEFFREY_PROJECT_PROP = "JEFFREY_CURRENT_PROJECT";
    private static final String JEFFREY_FILE_PATTERN_PROP = "JEFFREY_FILE_PATTERN";
    private static final String JEFFREY_PROFILER_CONFIG_PROP = "JEFFREY_PROFILER_CONFIG";
    private static final String JDK_JAVA_OPTIONS_PROP = "JDK_JAVA_OPTIONS";

    private static final String EXPORT_PREFIX = "export ";
    private static final String ASSIGN = "=";
    private static final String LINE_SEPARATOR = "\n";

    /**
     * @param exportJdkJavaOptions also export the profiler command as {@code JDK_JAVA_OPTIONS},
     *                             which the JVM picks up without an argfile
     */
    public record Context(
            SessionLayout layout,
            String profilerSettings,
            boolean exportJdkJavaOptions
    ) {}

    /**
     * Builds the content of the .env file.
     *
     * @param context the layout and settings to export
     * @return the content of the .env file with export statements
     */
    public String build(Context context) {
        SessionLayout layout = context.layout();
        List<String> exports = new ArrayList<>();

        if (layout.hasJeffreyHome()) {
            exports.add(export(JEFFREY_HOME_PROP, layout.jeffreyHome()));
        }
        exports.add(export(JEFFREY_WORKSPACES_PROP, layout.workspaces()));
        exports.add(export(JEFFREY_WORKSPACE_PROP, layout.workspace()));
        exports.add(export(JEFFREY_PROJECT_PROP, layout.project()));
        exports.add(export(JEFFREY_SESSION_PROP, layout.session()));
        exports.add(export(JEFFREY_FILE_PATTERN_PROP, layout.recordingFilePattern(DEFAULT_FILE_TEMPLATE)));

        if (context.profilerSettings() != null && !context.profilerSettings().isEmpty()) {
            String quoted = wrapQuotes(context.profilerSettings());
            exports.add(export(JEFFREY_PROFILER_CONFIG_PROP, quoted));
            if (context.exportJdkJavaOptions()) {
                exports.add(export(JDK_JAVA_OPTIONS_PROP, quoted));
            }
        }

        // Always newline-terminated. The old builder omitted it in the one case where
        // JDK_JAVA_OPTIONS was the last export, which no consumer relied on.
        return String.join(LINE_SEPARATOR, exports) + LINE_SEPARATOR;
    }

    private static String export(String name, Path value) {
        return export(name, value.toString());
    }

    private static String export(String name, String value) {
        return EXPORT_PREFIX + name + ASSIGN + value;
    }

    private static String wrapQuotes(String value) {
        return "'" + value + "'";
    }
}
