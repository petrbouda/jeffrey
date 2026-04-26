/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.local.core.resources.response;

import cafe.jeffrey.shared.common.InstantUtils;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;

import java.time.Clock;
import java.time.Instant;

public record InstanceSessionResponse(
        String id,
        String repositoryId,
        Long createdAt,
        Long finishedAt,
        boolean isActive,
        Long duration) {

    public static InstanceSessionResponse from(ProjectInstanceSessionInfo info, Clock clock) {
        Instant end = info.finishedAt() != null ? info.finishedAt() : clock.instant();
        long duration = end.toEpochMilli() - info.createdAt().toEpochMilli();

        return new InstanceSessionResponse(
                info.sessionId(),
                info.repositoryId(),
                InstantUtils.toEpochMilli(info.createdAt()),
                InstantUtils.toEpochMilli(info.finishedAt()),
                info.finishedAt() == null,
                duration);
    }
}
