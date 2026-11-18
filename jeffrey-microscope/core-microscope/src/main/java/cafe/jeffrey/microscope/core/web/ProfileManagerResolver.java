/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.microscope.core.web;

import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.profile.management.manager.ProfileManager;
import cafe.jeffrey.microscope.common.exception.Exceptions;

import java.util.Optional;

/**
 * Resolves a {@code profileId} to its {@link ProfileManager}. Every profile is built from a recording
 * and stored in the microscope-core database, so one lookup there answers for all of them.
 */
public class ProfileManagerResolver {

    private final RecordingsManager recordingsManager;

    public ProfileManagerResolver(RecordingsManager recordingsManager) {
        this.recordingsManager = recordingsManager;
    }

    public ProfileManager resolve(String profileId) {
        return find(profileId)
                .orElseThrow(() -> Exceptions.profileNotFound(profileId));
    }

    public Optional<ProfileManager> find(String profileId) {
        return recordingsManager.profile(profileId);
    }
}
