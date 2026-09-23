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

package cafe.jeffrey.ide.plugin.idea.dto;

import java.util.List;

/**
 * Self-description of a running IDE instance, returned by {@code GET /api/jeffrey/instance}.
 * Microscope aggregates this across all discovered instances to build its target picker, and routes
 * navigation by {@code (port, ProjectInfo.id)}.
 */
public record InstanceResponse(
        int protocolVersion,
        String instanceId,
        String ideName,
        String ideEdition,
        String ideVersion,
        long pid,
        int port,
        String startedAt,
        List<ProjectInfo> projects
) {
}
