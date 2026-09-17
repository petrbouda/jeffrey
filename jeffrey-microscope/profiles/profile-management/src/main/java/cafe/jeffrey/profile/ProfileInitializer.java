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

package cafe.jeffrey.profile;

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.provider.profile.api.RecordingSources;
import cafe.jeffrey.microscope.model.ProfileInfo;

import java.nio.file.Path;
import java.util.List;

public interface ProfileInitializer {

    /**
     * Builds the profile out of the recording's files.
     *
     * @param sources   the recording files to parse
     * @param artifacts the recording's supplementary files — perf counters, heap dumps — or an
     *                  empty list. Passed in rather than looked up by recording id: where they
     *                  live depends on how the recording arrived, and the caller is what knows.
     */
    ProfileManager initialize(ProfileInfo profileInfo, RecordingSources sources, List<Path> artifacts);

}
