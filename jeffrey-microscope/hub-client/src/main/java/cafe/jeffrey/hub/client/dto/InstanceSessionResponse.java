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

package cafe.jeffrey.hub.client.dto;

import cafe.jeffrey.shared.common.InstantUtils;
import cafe.jeffrey.microscope.model.ProjectInstanceSessionInfo;

import java.time.Clock;
import java.time.Instant;

public record InstanceSessionResponse(
        String id,
        String repositoryId,
        Long createdAt,
        Long finishedAt,
        boolean isActive,
        Long duration,
        boolean failed) {

    public static InstanceSessionResponse from(ProjectInstanceSessionInfo info, Clock clock) {
        Instant end = info.finishedAt() != null ? info.finishedAt() : clock.instant();
        long duration = end.toEpochMilli() - info.createdAt().toEpochMilli();

        return new InstanceSessionResponse(
                info.sessionId(),
                info.repositoryId(),
                InstantUtils.toEpochMilli(info.createdAt()),
                InstantUtils.toEpochMilli(info.finishedAt()),
                info.finishedAt() == null,
                duration,
                info.failed());
    }
}
