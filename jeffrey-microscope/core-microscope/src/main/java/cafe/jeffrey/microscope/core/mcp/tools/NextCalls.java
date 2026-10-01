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
package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.profile.mcp.McpNextTool;

/**
 * The one way Microscope starts a next call: {@link McpNextTool#call} with the weight the named
 * tool's own hints imply, read from {@link McpToolCatalogue}, so no call site states a weight and
 * none can state the wrong one.
 */
public final class NextCalls {

    private NextCalls() {
    }

    /** A call to the named tool; add its arguments and finish it with {@code why}. */
    public static McpNextTool.Call to(String tool) {
        return McpNextTool.call(tool, McpToolCatalogue.weightOf(tool));
    }
}
