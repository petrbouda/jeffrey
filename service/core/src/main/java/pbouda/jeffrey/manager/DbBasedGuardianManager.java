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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.ConfigBuilder;
import pbouda.jeffrey.common.rule.AnalysisItem;
import pbouda.jeffrey.guardian.Guardian;
import pbouda.jeffrey.guardian.GuardianResult;
import pbouda.jeffrey.repository.CacheRepository;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.util.List;

public class DbBasedGuardianManager implements GuardianManager {

    private final ProfileInfo profileInfo;
    private final WorkingDirs workingDirs;
    private final Guardian guardian;
    private final CacheRepository cacheRepository;

    public DbBasedGuardianManager(
            ProfileInfo profileInfo,
            WorkingDirs workingDirs,
            Guardian guardian,
            CacheRepository cacheRepository) {

        this.profileInfo = profileInfo;
        this.workingDirs = workingDirs;
        this.guardian = guardian;
        this.cacheRepository = cacheRepository;
    }

    @Override
    public List<AnalysisItem> guardResults() {
        Config config = new ConfigBuilder<>()
                .withPrimaryId(profileInfo.id())
                .withPrimaryRecordingDir(workingDirs.profileRecordingDir(profileInfo))
                .build();

        return guardian.process(config).stream()
                .map(GuardianResult::analysisItem)
                .toList();
    }
}
