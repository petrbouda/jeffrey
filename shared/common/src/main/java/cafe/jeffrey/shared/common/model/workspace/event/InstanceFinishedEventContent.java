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

package cafe.jeffrey.shared.common.model.workspace.event;

import java.time.Instant;

/**
 * Content of an instance-finished event, emitted when the last unfinished session of an
 * instance closes. Carries the transition timestamp, which is not otherwise reachable
 * from the event itself.
 */
public record InstanceFinishedEventContent(
        String instanceId,
        Instant finishedAt) {

    public InstanceFinishedEventContent {
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("instanceId must not be blank");
        }
    }
}
