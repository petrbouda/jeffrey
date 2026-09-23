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

package cafe.jeffrey.hub.core.manager.storage;


import java.util.List;

/**
 * Snapshot of the hub's on-disk storage usage. All byte figures are computed from
 * the filesystem at the time of the call — the hub database stores no size columns.
 */
public record StorageOverview(
        InfrastructureUsage infrastructure,
        List<ProjectStorage> projects) {

    /**
     * Storage used by the hub itself, outside of project repositories.
     */
    public record InfrastructureUsage(long databaseBytes, long tempBytes) {
    }

    /**
     * Storage used by a single project's recording repository: the bytes and the file count over
     * every session, and the newest file's timestamp. The hub does not read the files it stores,
     * so there is deliberately no breakdown by kind.
     */
    public record ProjectStorage(
            String workspaceId,
            String workspaceName,
            String projectId,
            String projectName,
            String projectLabel,
            long totalSizeBytes,
            int totalFiles,
            long lastActivityTimeMillis) {
    }
}
