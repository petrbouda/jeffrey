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
import cafe.jeffrey.microscope.model.ProjectInstanceInfo;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

public record InstanceResponse(
        String id,
        String instanceName,
        String status,
        Long createdAt,
        Long finishedAt,
        Long expiringAt,
        Long expiredAt,
        int sessionCount,
        String activeSessionId,
        Long duration,
        List<InstanceSessionResponse> sessions) {

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
                InstantUtils.toEpochMilli(info.expiringAt()),
                InstantUtils.toEpochMilli(info.expiredAt()),
                info.sessionCount(),
                info.activeSessionId(),
                duration,
                sessions);
    }
}
