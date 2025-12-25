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

package pbouda.jeffrey.profile.parser;

import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.ProjectInfo;

import java.util.function.Function;

/**
 * Parses JFR recordings and creates profiles in the database.
 * <p>
 * This is the entry point for profile creation in the Profile Domain.
 * Takes a recording identifier, parses the JFR file, stores events in DuckDB,
 * and returns the resulting ProfileInfo.
 */
public interface ProfileParser {

    /**
     * Factory for creating ProfileParser instances scoped to a specific project.
     */
    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, ProfileParser> {
    }

    /**
     * Parses a recording and creates a new profile.
     *
     * @param recordingId the ID of the recording to parse
     * @return the ProfileInfo of the newly created profile
     */
    ProfileInfo parse(String recordingId);
}
