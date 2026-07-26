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
 * Content of an instance-expired event, emitted when an instance's sessions have been
 * reclaimed or its last session deleted. Carries the transition timestamp, which is not
 * otherwise reachable from the event itself.
 */
public record InstanceExpiredEventContent(
        String instanceId,
        Instant expiredAt) {

    public InstanceExpiredEventContent {
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("instanceId must not be blank");
        }
    }
}
