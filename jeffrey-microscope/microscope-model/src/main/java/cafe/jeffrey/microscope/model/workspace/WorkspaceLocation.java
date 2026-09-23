/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.microscope.model.workspace;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record WorkspaceLocation(String location, Type type) {

    private static final WorkspaceLocation EMPTY_LOCATION = new WorkspaceLocation(null, null);

    public enum Type {
        FILE("file://"),
        HTTP("http://"),
        HTTPS("https://");

        private static final List<Type> VALUES = List.of(values());

        private final String prefix;

        Type(String prefix) {
            this.prefix = prefix;
        }

        public static Optional<Type> fromLocation(String location) {
            if (location == null || location.isBlank()) {
                throw new IllegalArgumentException("Location is null or blank");
            }

            for (Type type : VALUES) {
                if (location.toLowerCase().startsWith(type.prefix)) {
                    return Optional.of(type);
                }
            }

            return Optional.empty();
        }
    }

    public static WorkspaceLocation of(URI uri) {
        if (uri == null) {
            return EMPTY_LOCATION;
        }
        return of(uri.toString());
    }

    public static WorkspaceLocation of(Path path) {
        if (path == null) {
            return EMPTY_LOCATION;
        }
        return new WorkspaceLocation(path.toString(), Type.FILE);
    }

    public static WorkspaceLocation of(String location) {
        if (location == null || location.isBlank()) {
            return EMPTY_LOCATION;
        }

        Optional<Type> type = Type.fromLocation(location);
        if (type.isPresent()) {
            return new WorkspaceLocation(location.trim(), type.get());
        } else {
            throw new IllegalArgumentException("Location must start with: " + Type.VALUES);
        }
    }

    public boolean isEmpty() {
        return location == null || location.isBlank();
    }

    public Path toPath() {
        if (type == Type.FILE) {
            return Path.of(location);
        } else {
            throw new IllegalStateException("Location is not a file location: " + location);
        }
    }


    @Override
    public String toString() {
        return location;
    }
}
