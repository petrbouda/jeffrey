/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.api;

import pbouda.jeffrey.common.model.ProfileInfo;

import java.nio.file.Path;

public interface ProfileInitializer {

    /**
     * Start reading the events from the profile's source and populating to a connected writer.
     *
     * @param projectId             a project that the profile belongs to
     * @param originalRecordingPath a path to the original recording
     * @return a newly initialized profile
     */
    ProfileInfo newProfile(String projectId, Path originalRecordingPath);

}
