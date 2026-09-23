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

package cafe.jeffrey.hub.model.workspace;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Where a workspace lives, as a typed location string. A workspace with no location at all is
 * a {@code null} on {@link WorkspaceInfo}, never a location with nothing in it — one spelling
 * of "none", so that a reader checks one thing.
 */
public record WorkspaceLocation(String location, Type type) {

    public enum Type {
        FILE("file://"),
        HTTP("http://"),
        HTTPS("https://");

        private static final List<Type> VALUES = List.of(values());

        private final String prefix;

        Type(String prefix) {
            this.prefix = prefix;
        }

        private static Optional<Type> fromLocation(String location) {
            String lower = location.toLowerCase(Locale.ROOT);
            for (Type type : VALUES) {
                if (lower.startsWith(type.prefix)) {
                    return Optional.of(type);
                }
            }
            return Optional.empty();
        }
    }

    public WorkspaceLocation {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Location must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Location must have a type: " + location);
        }
    }

    public static WorkspaceLocation of(Path path) {
        return new WorkspaceLocation(path.toString(), Type.FILE);
    }

    public static WorkspaceLocation of(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Location must not be blank");
        }
        Type type = Type.fromLocation(location)
                .orElseThrow(() -> new IllegalArgumentException("Location must start with one of: " + Type.VALUES));
        return new WorkspaceLocation(location.trim(), type);
    }

    public Path toPath() {
        if (type == Type.FILE) {
            return Path.of(location);
        }
        throw new IllegalStateException("Location is not a file location: " + location);
    }

    @Override
    public String toString() {
        return location;
    }
}
