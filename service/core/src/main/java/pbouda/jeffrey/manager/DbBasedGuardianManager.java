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
import pbouda.jeffrey.common.rule.AnalysisItem;
import pbouda.jeffrey.jfr.configuration.ProfileInformationProvider;
import pbouda.jeffrey.repository.CacheRepository;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.util.List;

public class DbBasedGuardianManager implements GuardianManager {

    private final ProfileInformationProvider infoProvider;
    private final CacheRepository cacheRepository;

    public DbBasedGuardianManager(
            ProfileInfo profileInfo,
            WorkingDirs workingDirs,
            CacheRepository cacheRepository) {

        this.cacheRepository = cacheRepository;
        this.infoProvider = new ProfileInformationProvider(workingDirs.profileRecordings(profileInfo).getFirst());
    }

    @Override
    public List<AnalysisItem> guardResults() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
