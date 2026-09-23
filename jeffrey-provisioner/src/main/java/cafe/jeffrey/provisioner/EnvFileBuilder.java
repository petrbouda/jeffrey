/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import cafe.jeffrey.shared.common.HeartbeatConstants;

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
    private static final String JEFFREY_HEARTBEAT_DIR_PROP = "JEFFREY_HEARTBEAT_DIR";
    private static final String JEFFREY_HEARTBEAT_ENABLED_PROP = "JEFFREY_HEARTBEAT_ENABLED";
    private static final String JDK_JAVA_OPTIONS_PROP = "JDK_JAVA_OPTIONS";

    private static final String EXPORT_PREFIX = "export ";
    private static final String ASSIGN = "=";
    private static final String LINE_SEPARATOR = "\n";

    /**
     * @param exportJdkJavaOptions also export the profiler command as {@code JDK_JAVA_OPTIONS},
     *                             which the JVM picks up without an argfile
     * @param heartbeatEnabled     whether this session expects the {@code jeffrey-heartbeat}
     *                             library to report liveness. Exported so the library reads it,
     *                             and recorded in the session marker so the hub knows whether to
     *                             hold the session to its heartbeat deadline
     */
    public record Context(
            SessionLayout layout,
            String profilerSettings,
            boolean exportJdkJavaOptions,
            boolean heartbeatEnabled
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

        // Named outright rather than left to be derived from JEFFREY_CURRENT_SESSION, so the
        // library needs no opinion about where inside a session directory the files belong. The
        // library still falls back to deriving it, for a session provisioned before this export.
        exports.add(export(JEFFREY_HEARTBEAT_DIR_PROP,
                layout.session().resolve(HeartbeatConstants.HEARTBEAT_DIR)));
        exports.add(export(JEFFREY_HEARTBEAT_ENABLED_PROP, Boolean.toString(context.heartbeatEnabled())));

        // The flags reach the JVM through the argfile, or through JDK_JAVA_OPTIONS when that is
        // asked for. The .env file carries the layout, not a second copy of the command.
        if (context.exportJdkJavaOptions()
                && context.profilerSettings() != null && !context.profilerSettings().isEmpty()) {
            exports.add(export(JDK_JAVA_OPTIONS_PROP, wrapQuotes(context.profilerSettings())));
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
