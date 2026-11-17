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

package cafe.jeffrey.microscope.persistence.api;

/**
 * The IDE window a profile is linked to, as much of it as is worth keeping.
 *
 * <p>A link has a volatile half and a durable half. The port a window answered on and the pid it ran
 * under describe one run of one IDE process; they are wrong the moment either side restarts, which is
 * exactly when a stored link is read back. What survives is the choice the reader made — which
 * project, in which IDE — and discovery turns that back into a port when a jump next needs one.
 *
 * @param projectId   the IDE's stable identifier for the window ({@code Project.getLocationHash()}),
 *                    and what discovery matches on to find the window again
 * @param projectName the window's display name, kept so the UI can name the link without scanning
 * @param ideName     the IDE's product name, for the same reason
 * @param basePath    the project's directory on disk — the checkout this profile is about, and what
 *                    the {@code ide_} MCP tools resolve a frame against
 */
public record IdeTargetLink(
        String projectId,
        String projectName,
        String ideName,
        String basePath) {

    public IdeTargetLink {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId must not be blank");
        }
    }
}
