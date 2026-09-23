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
