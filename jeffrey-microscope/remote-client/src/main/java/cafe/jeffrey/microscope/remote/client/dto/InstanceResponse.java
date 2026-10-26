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

package cafe.jeffrey.microscope.remote.client.dto;

import cafe.jeffrey.shared.common.InstantUtils;
import cafe.jeffrey.microscope.model.ProjectInstanceInfo;
import cafe.jeffrey.microscope.model.ProjectInstanceInfo.ProjectInstanceStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

public record InstanceResponse(
        String id,
        String instanceName,
        String status,
        Long createdAt,
        Long finishedAt,
        int sessionCount,
        String activeSessionId,
        Long duration,
        List<InstanceSessionResponse> sessions) {

    /**
     * An instance as a remote lists it, without its sessions: named by its id,
     * and lasting until it finished — no duration while it runs.
     *
     * @param createdAt  epoch millis
     * @param finishedAt epoch millis; {@code null} while it runs
     */
    public static InstanceResponse summary(
            String id, ProjectInstanceStatus status, long createdAt, Long finishedAt, int sessionCount) {

        return new InstanceResponse(
                id,
                id,
                status.name(),
                createdAt,
                finishedAt,
                sessionCount,
                null,
                finishedAt != null ? finishedAt - createdAt : null,
                List.of());
    }

    public static InstanceResponse from(ProjectInstanceInfo info, Clock clock) {
        Instant end = info.finishedAt() != null ? info.finishedAt() : clock.instant();
        long duration = end.toEpochMilli() - info.startedAt().toEpochMilli();

        List<InstanceSessionResponse> sessions = info.sessions().stream()
                .map(s -> InstanceSessionResponse.from(s, clock))
                .toList();

        return new InstanceResponse(
                info.id(),
                info.instanceName(),
                info.status().name(),
                InstantUtils.toEpochMilli(info.startedAt()),
                InstantUtils.toEpochMilli(info.finishedAt()),
                info.sessionCount(),
                info.activeSessionId(),
                duration,
                sessions);
    }
}
