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
import java.util.function.Function;

/**
 * Resolves {@code <<ENV:NAME>>} from the process environment.
 *
 * <p>This is what Kubernetes cannot do for us: its own {@code $(VAR)} expansion only substitutes
 * variables declared earlier in the same container's {@code env:} list, so a value injected through
 * {@code envFrom}, a mutating webhook or the node environment arrives as literal text.
 *
 * <p>The lookup is injected rather than calling {@code System.getenv} directly so tests can drive
 * it from a map — the same {@code Function<String, String>} the rest of the provisioner threads
 * through.
 */
public record EnvPlaceholderSource(Function<String, String> envLookup) implements PlaceholderSource {

    public static final String TYPE = "ENV";

    public EnvPlaceholderSource {
        if (envLookup == null) {
            throw new IllegalArgumentException("envLookup must not be null");
        }
    }

    public static EnvPlaceholderSource ofSystem() {
        return new EnvPlaceholderSource(System::getenv);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Optional<String> lookup(String name) {
        String value = envLookup.apply(name);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}
