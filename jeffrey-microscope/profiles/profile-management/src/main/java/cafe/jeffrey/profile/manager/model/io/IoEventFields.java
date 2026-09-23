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

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.Type;

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
