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

package cafe.jeffrey.profile.mcp;

import cafe.jeffrey.microscope.mcp.protocol.McpCursor;
import cafe.jeffrey.microscope.mcp.protocol.McpServerIdentity;
import cafe.jeffrey.shared.common.JeffreyVersion;

/**
 * What Jeffrey's MCP server is, wherever the protocol asks the server to say: the identity every result
 * names, and the cursor codec its paged tools hand out, which names Jeffrey when it refuses a cursor
 * another version wrote.
 */
public final class JeffreyMcpServer {

    /** The server's name in {@code serverInfo}, which clients and the CI snapshot check read. */
    private static final String SERVER_NAME = "jeffrey";

    /** The product a refused cursor names. */
    private static final String PRODUCT_NAME = "Jeffrey";

    /** Jeffrey's name and build version, as every result states them. */
    public static final McpServerIdentity IDENTITY =
            new McpServerIdentity(SERVER_NAME, JeffreyVersion.resolveJeffreyVersion());

    /** The one cursor codec every paged tool encodes and decodes with. */
    public static final McpCursor CURSOR = new McpCursor(PRODUCT_NAME);

    private JeffreyMcpServer() {
    }
}
