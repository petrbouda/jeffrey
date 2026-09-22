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

/**
 * Every kind of configuration the hub may publish to a JVM. Closed on purpose: a published value
 * becomes a JVM option in the application's own argfile, so the set of things that can be said is a
 * security boundary, not a convenience. Adding a value is a deliberate act that must weigh what the
 * new option lets a hub user do inside someone else's process.
 *
 * <p>Identity only, and the name is the whole contract: a value's key in a published file is this
 * name lower-cased with dashes ({@link ScopedConfigLayout#hoconPath}), and the provisioner reads a
 * key spelled the same way. Nothing maps a type onto a key, which is why a value's name must be
 * chosen to be the setting's name.</p>
 */
public enum ConfigType {

    /**
     * The async-profiler command a session starts with, as {@code asprof-settings}. Carries exactly
     * what the hub could already push before scoped configuration existed, so publishing it grants
     * no reach that did not exist.
     */
    ASPROF_SETTINGS
}
