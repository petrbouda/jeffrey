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

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.Type;

/**
 * Shared field-name constants and per-event helpers for the socket/file I/O events
 * ({@code jdk.SocketRead}, {@code jdk.SocketWrite}, {@code jdk.FileRead}, {@code jdk.FileWrite}).
 */
final class IoEventFields {

    static final String HOST_FIELD = "host";
    static final String PORT_FIELD = "port";
    static final String PATH_FIELD = "path";
    static final String BYTES_READ_FIELD = "bytesRead";
    static final String BYTES_WRITTEN_FIELD = "bytesWritten";
    static final String EVENT_THREAD_FIELD = "eventThread";

    static final String SOCKET_READ_LABEL = "Socket Read";
    static final String SOCKET_WRITE_LABEL = "Socket Write";
    static final String FILE_READ_LABEL = "File Read";
    static final String FILE_WRITE_LABEL = "File Write";
    static final String UNKNOWN_PATH = "<unknown>";

    private IoEventFields() {
    }

    static boolean isRead(Type type) {
        return Type.SOCKET_READ.equals(type) || Type.FILE_READ.equals(type);
    }

    static boolean isSocket(Type type) {
        return Type.SOCKET_READ.equals(type) || Type.SOCKET_WRITE.equals(type);
    }

    static long bytes(Type type, ObjectNode fields) {
        String field = isRead(type) ? BYTES_READ_FIELD : BYTES_WRITTEN_FIELD;
        return Math.max(0, Json.readLong(fields, field));
    }

    static String socketPeer(ObjectNode fields) {
        String host = Json.readString(fields, HOST_FIELD);
        long port = Json.readLong(fields, PORT_FIELD);
        if (host == null) {
            host = UNKNOWN_PATH;
        }
        return port >= 0 ? host + ":" + port : host;
    }

    static String filePath(ObjectNode fields) {
        String path = Json.readString(fields, PATH_FIELD);
        return path == null ? UNKNOWN_PATH : path;
    }

    static String target(Type type, ObjectNode fields) {
        return isSocket(type) ? socketPeer(fields) : filePath(fields);
    }

    static String kindLabel(Type type) {
        if (Type.SOCKET_READ.equals(type)) {
            return SOCKET_READ_LABEL;
        }
        if (Type.SOCKET_WRITE.equals(type)) {
            return SOCKET_WRITE_LABEL;
        }
        if (Type.FILE_READ.equals(type)) {
            return FILE_READ_LABEL;
        }
        return FILE_WRITE_LABEL;
    }
}
