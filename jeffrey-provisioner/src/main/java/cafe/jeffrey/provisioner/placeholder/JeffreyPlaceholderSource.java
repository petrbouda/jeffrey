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

package cafe.jeffrey.provisioner.placeholder;

import cafe.jeffrey.provisioner.SessionLayout;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves {@code <<JEFFREY:NAME>>} to the paths this provisioner run produced.
 *
 * <p>The names mirror one-to-one the {@code JEFFREY_*} variables the run exports into its generated
 * {@code .env} file, so {@code <<JEFFREY:CURRENT_SESSION>>} and {@code $JEFFREY_CURRENT_SESSION}
 * always mean the same thing.
 *
 * <p>Deliberately absent: the resolved profiler command. It is an <em>output</em> of the run that
 * reaches the JVM through the argfile (or {@code JDK_JAVA_OPTIONS}), never a value a configuration
 * can refer back to.
 *
 * <p>Unlike {@code ENV} and {@code FILE}, these values only exist once the session directory has
 * been created, so this source joins the resolver in a later phase — see {@link Placeholders}.
 */
public record JeffreyPlaceholderSource(Map<String, String> values) implements PlaceholderSource {

    public static final String TYPE = "JEFFREY";

    public static final String HOME = "HOME";
    public static final String WORKSPACES = "WORKSPACES";
    public static final String CURRENT_WORKSPACE = "CURRENT_WORKSPACE";
    public static final String CURRENT_PROJECT = "CURRENT_PROJECT";
    public static final String CURRENT_SESSION = "CURRENT_SESSION";
    public static final String FILE_PATTERN = "FILE_PATTERN";
    public static final String PROFILER_PATH = "PROFILER_PATH";

    public JeffreyPlaceholderSource {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        values = Map.copyOf(values);
    }

    /**
     * Binds the names to the paths a run produced. {@code jeffreyHome} is absent when the run was
     * configured with an explicit workspaces directory, and {@code profilerPath} when no profiler
     * was resolved; both are simply left unbound.
     */
    public static JeffreyPlaceholderSource of(
            SessionLayout layout, String profilerPath, String filePatternTemplate) {

        Map<String, String> values = new HashMap<>();
        put(values, HOME, layout.jeffreyHome());
        put(values, WORKSPACES, layout.workspaces());
        put(values, CURRENT_WORKSPACE, layout.workspace());
        put(values, CURRENT_PROJECT, layout.project());
        put(values, CURRENT_SESSION, layout.session());
        put(values, FILE_PATTERN, layout.recordingFilePattern(filePatternTemplate));
        if (profilerPath != null && !profilerPath.isBlank()) {
            values.put(PROFILER_PATH, profilerPath);
        }
        return new JeffreyPlaceholderSource(values);
    }

    /**
     * A source bound to the session directory alone. Used where the rest of the layout is not in
     * scope, and by tests that only exercise session-path substitution.
     */
    public static JeffreyPlaceholderSource ofSession(Path session) {
        return new JeffreyPlaceholderSource(Map.of(CURRENT_SESSION, session.toString()));
    }

    private static void put(Map<String, String> values, String name, Path path) {
        if (path != null) {
            values.put(name, path.toString());
        }
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Optional<String> lookup(String name) {
        return Optional.ofNullable(values.get(name));
    }
}
