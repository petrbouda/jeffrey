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

package pbouda.jeffrey.profile.creator;

import pbouda.jeffrey.shared.common.model.ProjectInfo;

import java.util.function.Function;

public interface ProfileCreator {

    /**
     * Creates a new profile from a recording by parsing the JFR file
     * and storing events to the database.
     *
     * @param recordingId the ID of the recording to parse
     * @return the ID of the created profile
     */
    String createProfile(String recordingId);

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, ProfileCreator> {
    }
}
