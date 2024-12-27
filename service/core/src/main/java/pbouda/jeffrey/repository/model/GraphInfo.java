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

import java.time.Instant;
import java.util.UUID;

public record GraphInfo(
        String id,
        String profileId,
        boolean useThreadMode,
        boolean useWeight,
        String name,
        Instant createdAt
) {

    public GraphInfo(
            String profileId,
            boolean useThreadMode,
            boolean useWeight,
            String name) {

        this(UUID.randomUUID().toString(),
                profileId,
                useThreadMode,
                useWeight,
                name,
                Instant.now());
    }

    public static GraphInfo custom(
            String profileId,
            boolean useThreadMode,
            boolean useWeight,
            String name) {

        return new GraphInfo(profileId, useThreadMode, useWeight, name);
    }
}
