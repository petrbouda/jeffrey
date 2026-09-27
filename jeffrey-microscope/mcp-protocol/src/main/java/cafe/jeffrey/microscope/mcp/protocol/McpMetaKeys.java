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
 * The {@code _meta} keys the protocol reads and writes.
 * <p>
 * The stateless revision ({@code 2026-07-28}) defines the four under {@code io.modelcontextprotocol/}:
 * a request carries the first three in {@code params._meta}, a result carries the last one. A request
 * may also carry the W3C trace context there, under the names the W3C header uses. The keys a server
 * adds to a tool's own {@code _meta} in {@code tools/list} are its own, and live with it.
 */
public final class McpMetaKeys {

    public static final String FIELD_META = "_meta";

    public static final String PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion";
    public static final String CLIENT_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities";
    public static final String CLIENT_INFO = "io.modelcontextprotocol/clientInfo";
    public static final String SERVER_INFO = "io.modelcontextprotocol/serverInfo";

    /** The W3C trace context of the caller's own trace, verbatim. */
    public static final String TRACEPARENT = "traceparent";
    public static final String TRACESTATE = "tracestate";

    private McpMetaKeys() {
    }
}
