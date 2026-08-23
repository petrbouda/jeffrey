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

package cafe.jeffrey.provisioner.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * How one {@code JEFFREY_*} environment variable becomes HOCON entries.
 *
 * <p>Most variables map straight onto a configuration path. The three that do not — attributes,
 * heap dumps and JVM logging — carry a small syntax of their own and set more than one path, so
 * each gets a binding rather than a branch in a shared parser.
 *
 * <p>Every binding follows the same rule for a value it cannot read: <b>contribute nothing and
 * warn</b>. The variable then simply does not appear in the environment layer, the layer below
 * supplies the value, and the provisioner's contract holds — a misconfiguration starts the
 * application without profiling rather than blocking its startup.
 *
 * <p>Sealed rather than open because the provisioner is compiled to a GraalVM native image:
 * bindings are listed explicitly in {@link EnvironmentLayer}, never discovered by reflection.
 */
public sealed interface EnvBinding {

    Logger LOG = LoggerFactory.getLogger(EnvBinding.class);

    /** The environment variable this binding reads. */
    String envName();

    /** Adds this binding's HOCON entries to {@code entries}, keyed by path expression. */
    void bind(String rawValue, Map<String, Object> entries);

    /** A value copied to {@code path} as-is. Placeholders in it are resolved after the merge. */
    record Value(String envName, String path) implements EnvBinding {

        @Override
        public void bind(String rawValue, Map<String, Object> entries) {
            entries.put(path, rawValue);
        }
    }

    /**
     * A boolean at {@code path}. Only {@code true} and {@code false} are accepted, case-insensitive;
     * anything else is rejected rather than guessed at, because reading an unrecognized value as
     * {@code false} would let a typo silently disable a feature.
     */
    record Flag(String envName, String path) implements EnvBinding {

        private static final String TRUE = "true";
        private static final String FALSE = "false";

        @Override
        public void bind(String rawValue, Map<String, Object> entries) {
            String normalized = rawValue.trim().toLowerCase();
            if (TRUE.equals(normalized)) {
                entries.put(path, Boolean.TRUE);
                return;
            }
            if (FALSE.equals(normalized)) {
                entries.put(path, Boolean.FALSE);
                return;
            }
            LOG.warn("Unrecognized boolean environment value, ignoring it: name={} value={} expected={}",
                    envName, rawValue, Set.of(TRUE, FALSE));
        }
    }

    /**
     * {@code key=value,key=value} into the {@code attributes} object. A malformed pair is skipped
     * rather than failing the whole variable — attributes are metadata, and losing one must not
     * cost the other nine.
     */
    record Attributes(String envName, String path) implements EnvBinding {

        private static final String PAIR_SEPARATOR = ",";
        private static final String KEY_VALUE_SEPARATOR = "=";

        @Override
        public void bind(String rawValue, Map<String, Object> entries) {
            Map<String, Object> attributes = new HashMap<>();
            for (String pair : rawValue.split(PAIR_SEPARATOR)) {
                int separatorIndex = pair.indexOf(KEY_VALUE_SEPARATOR);
                if (separatorIndex <= 0) {
                    LOG.warn("Skipping malformed attribute pair (expected key=value): pair={}", pair.trim());
                    continue;
                }
                String key = pair.substring(0, separatorIndex).trim();
                if (key.isEmpty()) {
                    LOG.warn("Skipping attribute pair with empty key: pair={}", pair.trim());
                    continue;
                }
                attributes.put(key, pair.substring(separatorIndex + 1).trim());
            }
            if (!attributes.isEmpty()) {
                entries.put(path, attributes);
            }
        }
    }

    /**
     * {@code exit} | {@code crash} | {@code off} into the {@code heap-dump} block, which is a
     * flag and a type rather than a single value.
     */
    record HeapDump(String envName, String enabledPath, String typePath) implements EnvBinding {

        private static final String OFF = "off";
        private static final Set<String> TYPES = Set.of("exit", "crash");

        @Override
        public void bind(String rawValue, Map<String, Object> entries) {
            String normalized = rawValue.trim().toLowerCase();
            if (OFF.equals(normalized)) {
                entries.put(enabledPath, Boolean.FALSE);
                return;
            }
            if (!TYPES.contains(normalized)) {
                LOG.warn("Unrecognized heap dump environment value, ignoring it: name={} value={} expected={}",
                        envName, rawValue, TYPES);
                return;
            }
            entries.put(enabledPath, Boolean.TRUE);
            entries.put(typePath, normalized);
        }
    }

    /** An {@code -Xlog} command. Naming one is what turns JVM logging on, so the flag follows. */
    record JvmLogging(String envName, String enabledPath, String commandPath) implements EnvBinding {

        @Override
        public void bind(String rawValue, Map<String, Object> entries) {
            entries.put(enabledPath, Boolean.TRUE);
            entries.put(commandPath, rawValue);
        }
    }
}
