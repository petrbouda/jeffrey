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

package pbouda.jeffrey.profile.guardian;

import pbouda.jeffrey.common.ProfilingStartEnd;
import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.common.config.ConfigBuilder;
import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.common.model.profile.ProfileInfo;
import pbouda.jeffrey.common.time.RelativeTimeRange;

import java.util.List;

public class ParsingGuardianProvider implements GuardianProvider {

    private final ProfileDirs profileDirs;
    private final Guardian guardian;

    public ParsingGuardianProvider(ProfileDirs profileDirs, Guardian guardian) {
        this.profileDirs = profileDirs;
        this.guardian = guardian;
    }

    @Override
    public List<GuardianResult> get() {
        ProfileInfo profileInfo = profileDirs.readInfo()
                .orElseThrow(() -> new IllegalStateException("Profile info is missing"));

        ProfilingStartEnd primaryStartEnd = new ProfilingStartEnd(
                profileInfo.startedAt(), profileInfo.finishedAt());

        Config config = new ConfigBuilder<>()
                .withPrimaryId(profileInfo.id())
                .withPrimaryRecordingDir(profileDirs.recordingsDir())
                .withPrimaryStartEnd(primaryStartEnd)
                .build();

        return guardian.process(config);
    }
}
