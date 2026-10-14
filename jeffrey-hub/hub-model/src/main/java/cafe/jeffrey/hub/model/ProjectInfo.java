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

package cafe.jeffrey.hub.model;

import java.time.Instant;
import java.util.Map;

/**
 * A project as its {@code .project-info.json} on the volume declares it.
 *
 * @param id          the provisioner's project id
 * @param workspaceId the workspace directory the project lies in
 * @param createdAt   when the provisioner created it
 * @param attributes  the labels the provisioner was given
 */
public record ProjectInfo(
        String id,
        String name,
        String workspaceId,
        Instant createdAt,
        Map<String, String> attributes) {

    public ProjectInfo {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
