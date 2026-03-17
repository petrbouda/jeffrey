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

package pbouda.jeffrey.platform.resources.response;

import pbouda.jeffrey.shared.common.InstantUtils;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo;

public record InstanceResponse(
        String id,
        String hostname,
        String status,
        Long startedAt,
        Long finishedAt,
        Long expiringAt,
        Long expiredAt,
        int sessionCount,
        String activeSessionId) {

    public static InstanceResponse from(ProjectInstanceInfo info) {
        return new InstanceResponse(
                info.id(),
                info.hostname(),
                info.status().name(),
                InstantUtils.toEpochMilli(info.startedAt()),
                InstantUtils.toEpochMilli(info.finishedAt()),
                InstantUtils.toEpochMilli(info.expiringAt()),
                InstantUtils.toEpochMilli(info.expiredAt()),
                info.sessionCount(),
                info.activeSessionId());
    }
}
