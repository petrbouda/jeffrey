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

package cafe.jeffrey.hub.core.config;

import cafe.jeffrey.shared.common.config.ConfigType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Checks a configuration value before it is stored, by its type.
 *
 * <p>A map rather than a switch, and checked at class initialization, so a type added to the
 * catalogue without someone deciding what a valid value for it is fails immediately and loudly
 * rather than being stored unchecked.</p>
 *
 * <p>Every rule throws {@link IllegalArgumentException} naming what failed, which the gRPC boundary
 * turns into {@code INVALID_ARGUMENT} and the editor shows as it stands.</p>
 */
public abstract class ConfigValueValidators {

    private ConfigValueValidators() {
    }

    private static final Map<ConfigType, Consumer<String>> VALIDATORS = validators();

    private static Map<ConfigType, Consumer<String>> validators() {
        Map<ConfigType, Consumer<String>> validators = new EnumMap<>(ConfigType.class);
        validators.put(ConfigType.ASPROF_SETTINGS, ConfigValueValidators::validateAsprofSettings);

        for (ConfigType type : ConfigType.values()) {
            if (!validators.containsKey(type)) {
                throw new IllegalStateException("Config type has no validator: type=" + type);
            }
        }
        return Map.copyOf(validators);
    }

    /**
     * @throws IllegalArgumentException when the value is not valid for its type
     */
    public static void validate(ConfigType type, String value) {
        VALIDATORS.get(type).accept(value);
    }

    /** Generous, but bounded: a value this long is a mistake, and the column is not a blob store. */
    private static final int MAX_LENGTH = 8192;

    private static final Pattern JEFFREY_PLACEHOLDER = Pattern.compile("<<JEFFREY:([A-Z_]+)(?::-[^>]*)?>>");

    /**
     * The placeholder names the provisioner can answer, copied from {@code JeffreyPlaceholderSource}
     * rather than shared: the provisioner is not on the hub's class path, and a name it cannot
     * resolve silently becomes an empty string in a JVM's command line. Catching it here is what
     * turns that into an error the operator sees while they are still editing.
     */
    private static final Set<String> KNOWN_PLACEHOLDERS = Set.of(
            "HOME", "WORKSPACES", "CURRENT_WORKSPACE", "CURRENT_PROJECT", "CURRENT_SESSION",
            "FILE_PATTERN", "PROFILER_PATH");

    /** An argfile is line-based, so a value spanning lines would not survive the round trip. */
    private static final Pattern FORBIDDEN_CHARACTERS = Pattern.compile("[\\r\\n\\u0000]");

    /**
     * Checks an async-profiler command. The command is passed through to a JVM's argfile, so the
     * rules are about what would make that file malformed or the value meaningless, not about
     * whether the profiler will like it — the hub does not know async-profiler's grammar and should
     * not pretend to.
     */
    private static void validateAsprofSettings(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The profiler command must not be empty");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "The profiler command is longer than " + MAX_LENGTH + " characters");
        }
        if (FORBIDDEN_CHARACTERS.matcher(value).find()) {
            throw new IllegalArgumentException(
                    "The profiler command must be a single line with no control characters");
        }
        rejectUnknownPlaceholders(value);
    }

    private static void rejectUnknownPlaceholders(String value) {
        List<String> unknown = JEFFREY_PLACEHOLDER.matcher(value).results()
                .map(result -> result.group(1))
                .filter(name -> !KNOWN_PLACEHOLDERS.contains(name))
                .distinct()
                .toList();

        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "The profiler command uses placeholders nothing can resolve: " + unknown
                            + "; known placeholders are " + KNOWN_PLACEHOLDERS.stream().sorted().toList());
        }
    }
}
