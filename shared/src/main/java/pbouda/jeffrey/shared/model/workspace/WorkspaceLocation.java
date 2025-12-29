/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.shared.model.workspace;

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

    public URI toUri() {
        if (type == Type.HTTP || type == Type.HTTPS) {
            return URI.create(location);
        } else {
            throw new IllegalStateException("Location is not a HTTP/HTTPS location: " + location);
        }
    }

    @Override
    public String toString() {
        return location;
    }
}
