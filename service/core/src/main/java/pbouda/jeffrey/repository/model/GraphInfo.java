/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.repository.model;

import pbouda.jeffrey.common.Type;

import java.time.Instant;
import java.util.UUID;

public record GraphInfo(
        String id,
        String profileId,
        Type eventType,
        boolean useThreadMode,
        boolean useWeight,
        boolean complete,
        String name,
        Instant createdAt
) {

    public GraphInfo(
            String profileId,
            Type eventType,
            boolean useThreadMode,
            boolean useWeight,
            boolean complete,
            String name) {

        this(UUID.randomUUID().toString(),
                profileId,
                eventType,
                useThreadMode,
                useWeight,
                complete,
                name,
                Instant.now());
    }

    public static GraphInfo custom(
            String profileId,
            Type eventType,
            boolean useThreadMode,
            boolean useWeight,
            String name) {

        return new GraphInfo(profileId, eventType, useThreadMode, useWeight, false, name);
    }
}
