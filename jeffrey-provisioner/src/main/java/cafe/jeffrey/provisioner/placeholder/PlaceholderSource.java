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

import java.util.Optional;

/**
 * One way of looking up a placeholder value. The type prefix in {@code <<TYPE:NAME>>} selects the
 * source, so {@code <<ENV:SF_CLUSTER>>} asks {@link EnvPlaceholderSource} for {@code SF_CLUSTER}.
 *
 * <p>Sealed rather than open because the provisioner is compiled to a GraalVM native image: sources
 * are wired explicitly into {@link Placeholders}, never discovered by reflection or
 * {@code ServiceLoader}.
 */
public sealed interface PlaceholderSource
        permits EnvPlaceholderSource, JeffreyPlaceholderSource {

    /** The type prefix this source answers for, e.g. {@code ENV}. */
    String type();

    /** The value bound to {@code name}, or empty when this source cannot supply one. */
    Optional<String> lookup(String name);
}
