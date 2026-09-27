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

package cafe.jeffrey.microscope.mcp.protocol;

/**
 * The JSON-RPC and MCP error codes this server answers with, and the HTTP status each carries.
 * <p>
 * Two rows share {@code -32602}: {@link #INVALID_PARAMS} is an argument a tool or method refused, or a
 * resource that is not there (answered in a 200), and {@link #INVALID_META} is a {@code params} or
 * {@code _meta} the transport refuses before dispatch — params that are not an object, a missing
 * {@code _meta} protocol version (every handshake-era client, {@code initialize} included), client
 * capabilities that are missing (a 400). {@code -32002} is not here: {@code 2026-07-28} forbids it.
 * <p>
 * {@link #HEADER_MISMATCH} is checked before {@link #UNSUPPORTED_VERSION}: a version header that does
 * not repeat the {@code _meta} version is a mismatch even when it names a supported revision, and only
 * a header and {@code _meta} that agree on an unsupported revision get {@code -32022}.
 */
public enum McpErrorCode {

    PARSE_ERROR(-32700, 400),
    INVALID_REQUEST(-32600, 400),
    METHOD_NOT_FOUND(-32601, 404),
    INVALID_PARAMS(-32602, 200),
    INVALID_META(-32602, 400),
    INTERNAL(-32603, 200),
    HEADER_MISMATCH(-32020, 400),
    MISSING_CAPABILITY(-32021, 400),
    UNSUPPORTED_VERSION(-32022, 400);

    private final int code;
    private final int httpStatus;

    McpErrorCode(int code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    /** The HTTP status a request answered with this code carries. */
    public int httpStatus() {
        return httpStatus;
    }
}
