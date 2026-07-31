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

package cafe.jeffrey.microscope.core.configuration;

import org.springframework.core.env.EnumerablePropertySource;
import cafe.jeffrey.shared.common.config.SettingsStore;

/**
 * Exposes the live {@link SettingsStore} to Spring's {@code Environment}.
 * <p>
 * The previous implementation copied the resolved settings into a {@code MapPropertySource} at
 * startup, which froze them for the lifetime of the JVM. Reading through the store instead makes
 * {@code environment.getProperty(...)} reflect the current value: Spring's property resolver does not
 * cache, so a write to the store is visible on the next read.
 * <p>
 * The store's key set is fixed at construction, so {@link #getPropertyNames()} is stable — Spring
 * assumes an enumerable source does not silently gain keys.
 */
public class SettingsPropertySource extends EnumerablePropertySource<SettingsStore> {

    public static final String NAME = "jeffrey-db-settings";

    public SettingsPropertySource(SettingsStore store) {
        super(NAME, store);
    }

    @Override
    public String[] getPropertyNames() {
        return getSource().names().toArray(String[]::new);
    }

    @Override
    public Object getProperty(String name) {
        return getSource().get(name);
    }

    @Override
    public boolean containsProperty(String name) {
        return getSource().contains(name);
    }
}
