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
