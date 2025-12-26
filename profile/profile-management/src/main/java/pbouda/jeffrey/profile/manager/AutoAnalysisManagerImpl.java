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

package pbouda.jeffrey.profile.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.common.analysis.AutoAnalysisResult;
import pbouda.jeffrey.common.persistence.CacheKey;
import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;

import java.util.List;
import java.util.Optional;

public class AutoAnalysisManagerImpl implements AutoAnalysisManager {

    private static final Logger LOG = LoggerFactory.getLogger(AutoAnalysisManagerImpl.class);

    private static final TypeReference<List<AutoAnalysisResult>> ANALYSIS_RESULT_TYPE =
            new TypeReference<List<AutoAnalysisResult>>() {
            };

    private final ProfileCacheRepository cacheRepository;

    public AutoAnalysisManagerImpl(ProfileCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    @Override
    public List<AutoAnalysisResult> analysisResults() {
        Optional<List<AutoAnalysisResult>> results = cacheRepository.get(
                CacheKey.PROFILE_AUTO_ANALYSIS, ANALYSIS_RESULT_TYPE);

        if (results.isPresent()) {
            return results.get();
        } else {
            LOG.warn("Auto Analysis is missing in the cache database.");
            return List.of();
        }
    }
}
