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

package cafe.jeffrey.shared.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Live, mutable view of the resolved application settings. Written by the settings write path when a
 * user saves a change, read by every consumer that needs the current value rather than the value that
 * happened to be in effect at startup.
 * <p>
 * The key set is fixed at construction from the declared settings and never grows: unknown names are
 * rejected on write and unknown rows loaded from the database are ignored. That keeps the key set
 * stable for the Spring property source built on top of this store.
 * <p>
 * Secrets are held here in <em>plaintext</em> — the encrypted form only ever exists in the database.
 * <p>
 * Every typed getter is total. A malformed value is logged and falls back to the setting's declared
 * default, then to the caller's fallback; getters never throw, because they are called from request
 * paths where a bad stored value must not become a failed request.
 */
public final class SettingsStore {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsStore.class);

    private final Map<String, String> defaults;
    private final ConcurrentHashMap<String, String> values;

    /**
     * @param defaults      declared settings and their default values; defines the permanent key set
     * @param initialValues resolved overrides (already decrypted); entries with unknown names are ignored
     */
    public SettingsStore(Map<String, String> defaults, Map<String, String> initialValues) {
        this.defaults = Map.copyOf(defaults);
        this.values = new ConcurrentHashMap<>(this.defaults);

        initialValues.forEach((name, value) -> {
            if (this.defaults.containsKey(name)) {
                this.values.put(name, value == null ? "" : value);
            } else {
                LOG.warn("Ignoring unknown stored setting: name={}", name);
            }
        });
    }

    /**
     * Updates a known setting.
     *
     * @return true when the stored value actually changed
     * @throws IllegalArgumentException when the name was not declared
     */
    public boolean put(String name, String value) {
        if (!defaults.containsKey(name)) {
            throw new IllegalArgumentException("Unknown setting: " + name);
        }

        String replacement = value == null ? "" : value;
        String previous = values.put(name, replacement);
        return !Objects.equals(previous, replacement);
    }

    /**
     * @return the current raw value, or {@code null} when the name was not declared
     */
    public String get(String name) {
        return values.get(name);
    }

    public boolean contains(String name) {
        return values.containsKey(name);
    }

    /**
     * @return every declared setting name; stable for the lifetime of the store
     */
    public Set<String> names() {
        return Collections.unmodifiableSet(values.keySet());
    }

    public String getString(String name, String fallback) {
        String value = values.get(name);
        return value != null ? value : fallback;
    }

    public int getInt(String name, int fallback) {
        return resolve(name, fallback, Integer::parseInt);
    }

    public double getDouble(String name, double fallback) {
        return resolve(name, fallback, Double::parseDouble);
    }

    private <T> T resolve(String name, T fallback, Function<String, T> parser) {
        T current = parse(name, values.get(name), parser);
        if (current != null) {
            return current;
        }

        T declared = parse(name, defaults.get(name), parser);
        return declared != null ? declared : fallback;
    }

    private static <T> T parse(String name, String value, Function<String, T> parser) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return parser.apply(value.trim());
        } catch (RuntimeException e) {
            LOG.warn("Malformed setting value, falling back: name={} value={}", name, value);
            return null;
        }
    }
}
