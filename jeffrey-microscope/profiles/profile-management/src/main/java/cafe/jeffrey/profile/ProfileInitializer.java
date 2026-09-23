/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
