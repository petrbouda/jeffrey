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

package cafe.jeffrey.microscope.core.mcp;

/**
 * Every top-level page of the Microscope application shell that an MCP answer links to, by the path
 * the router serves it at under {@code /}. Kept apart from {@link MicroscopeView}, which is always
 * inside one profile, so a global page cannot be built with a profile id and a view cannot be built
 * without one. The enforcement test holds every constant to the router's pinned
 * {@code global-routes.json}.
 */
public enum MicroscopePage {

    /** The Quick Analysis store: every recording, analysed or not, with the profile built from it. */
    RECORDINGS("recordings"),

    /** The hub browser: every connected hub, its workspaces, projects and recording sessions. */
    HUBS("hubs");

    private final String path;

    MicroscopePage(String path) {
        this.path = path;
    }

    /** The path under {@code /} the router serves this page at. */
    public String path() {
        return path;
    }
}
