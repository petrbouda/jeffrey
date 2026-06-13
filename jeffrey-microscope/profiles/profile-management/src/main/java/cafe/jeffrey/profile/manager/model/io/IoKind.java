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

package cafe.jeffrey.profile.manager.model.io;

import cafe.jeffrey.shared.common.model.Type;

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
