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

package cafe.jeffrey.shared.ui.hub.dto;

import cafe.jeffrey.recordings.core.download.DownloadTask;

import java.time.Instant;
import java.util.List;

/**
 * Response model for a download task.
 */
public record DownloadTaskResponse(
        String taskId,
        String sessionId,
        List<String> fileIds,
        String status,
        Instant createdAt
) {
    public static DownloadTaskResponse from(DownloadTask task) {
        return new DownloadTaskResponse(
                task.getTaskId(),
                task.getSessionId(),
                task.getFileIds(),
                task.getCurrentProgress().status().name(),
                task.getCreatedAt()
        );
    }
}
