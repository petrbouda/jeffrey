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

package cafe.jeffrey.provisioner.placeholder;

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
 * <p>Deliberately absent: {@code PROFILER_CONFIG}. It is an <em>output</em> of the run, and feeding
 * a previous run's fully-resolved command back in is the trap {@code InitConfig} already guards
 * against for the environment variable of that name.
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

    /**
     * Everything a completed {@code init} knows about where it put things. {@code jeffreyHome} is
     * null when the run was configured with an explicit workspaces directory instead, and
     * {@code profilerPath} is null when no profiler was resolved; both are simply left unbound.
     */
    public record Layout(
            Path jeffreyHome,
            Path workspaces,
            Path workspace,
            Path project,
            Path session,
            String profilerPath) {
    }

    public JeffreyPlaceholderSource {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        values = Map.copyOf(values);
    }

    public static JeffreyPlaceholderSource of(Layout layout, String filePatternTemplate) {
        Map<String, String> values = new HashMap<>();
        put(values, HOME, layout.jeffreyHome());
        put(values, WORKSPACES, layout.workspaces());
        put(values, CURRENT_WORKSPACE, layout.workspace());
        put(values, CURRENT_PROJECT, layout.project());
        put(values, CURRENT_SESSION, layout.session());
        if (layout.session() != null) {
            put(values, FILE_PATTERN, layout.session().resolve(filePatternTemplate));
        }
        if (layout.profilerPath() != null && !layout.profilerPath().isBlank()) {
            values.put(PROFILER_PATH, layout.profilerPath());
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
