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

package cafe.jeffrey.profile.manager.model.io;

import cafe.jeffrey.microscope.model.Type;

import java.util.List;

/**
 * The two flavours of blocking I/O Jeffrey visualizes on separate pages. Socket I/O is identified by
 * a {@code host:port} peer; file I/O by a path. Each kind maps to its pair of JFR read/write events.
 */
public enum IoKind {
    SOCKET(List.of(Type.SOCKET_READ, Type.SOCKET_WRITE)),
    FILE(List.of(Type.FILE_READ, Type.FILE_WRITE));

    private final List<Type> types;

    IoKind(List<Type> types) {
        this.types = types;
    }

    public List<Type> types() {
        return types;
    }

    /**
     * Resolves the {@code socket} / {@code file} path segment to a kind, throwing for anything else.
     */
    public static IoKind fromPath(String value) {
        return switch (value == null ? "" : value.toLowerCase()) {
            case "socket" -> SOCKET;
            case "file" -> FILE;
            default -> throw new IllegalArgumentException("Unknown I/O kind: " + value);
        };
    }
}
