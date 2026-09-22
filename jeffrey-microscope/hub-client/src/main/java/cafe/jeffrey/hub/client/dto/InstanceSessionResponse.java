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

package cafe.jeffrey.hub.client.dto;

import cafe.jeffrey.shared.common.InstantUtils;
import cafe.jeffrey.microscope.model.ProjectInstanceSessionInfo;

import cafe.jeffrey.shared.common.config.ConfigScope;
import cafe.jeffrey.shared.common.model.repository.AppliedConfigLayer;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * One recording session as the UI sees it.
 *
 * <p>The profiler fields describe what the run was started with, as its provisioner recorded it:
 * the resolved command, the configuration layer its base came from, and the hub-published files
 * that were merged with the digest each had when it was read. Comparing those digests with what
 * the hub holds now is what tells a reader whether a running JVM is still current. All three are
 * absent for a session declared by a provisioner too old to record them.</p>
 */
public record InstanceSessionResponse(
        String id,
        String repositoryId,
        Long createdAt,
        Long finishedAt,
        boolean isActive,
        Long duration,
        boolean failed,
        String profilerCommandSource,
        String profilerCommand,
        List<AppliedConfigLayerResponse> configLayers) {

    /** One hub-published file a session merged, and which version of it. */
    public record AppliedConfigLayerResponse(ConfigScope scope, String digest) {
    }

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
                info.failed(),
                info.profilerCommandSource(),
                info.profilerCommand(),
                info.configLayers().stream()
                        .map(layer -> new AppliedConfigLayerResponse(layer.scope(), layer.digest()))
                        .toList());
    }
}
