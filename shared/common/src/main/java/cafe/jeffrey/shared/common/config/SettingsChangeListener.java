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

import java.util.Set;

/**
 * Notified after a settings change has been applied to the {@link SettingsStore}.
 * <p>
 * Only implement this when a change requires rebuilding something expensive — an SDK client, a
 * logging system reconfiguration. Consumers that can simply read the current value should take the
 * store as a constructor argument and read it on each use instead; that needs no listener and cannot
 * go stale.
 */
public interface SettingsChangeListener {

    /**
     * @return the setting names this listener reacts to; it is skipped when a change touches none of them
     */
    Set<String> observedSettings();

    /**
     * Applies the current settings. Invoked on the dispatcher thread, never concurrently with itself
     * or another listener, and always after the store already holds the new values.
     *
     * @param store the live settings, already updated
     */
    void onChanged(SettingsStore store);
}
