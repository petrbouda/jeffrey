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

package pbouda.jeffrey.profile.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import pbouda.jeffrey.common.analysis.AutoAnalysisResult;
import pbouda.jeffrey.common.persistence.CacheKey;
import pbouda.jeffrey.common.persistence.CacheRepository;

import java.util.List;
import java.util.Optional;

public class CachingAutoAnalysisProvider implements AutoAnalysisProvider {

    private static final TypeReference<List<AutoAnalysisResult>> ANALYSIS_RESULT_TYPE =
            new TypeReference<List<AutoAnalysisResult>>() {
            };

    private final AutoAnalysisProvider autoAnalysisProvider;
    private final CacheRepository cacheRepository;

    public CachingAutoAnalysisProvider(AutoAnalysisProvider autoAnalysisProvider, CacheRepository cacheRepository) {
        this.autoAnalysisProvider = autoAnalysisProvider;
        this.cacheRepository = cacheRepository;
    }

    @Override
    public List<AutoAnalysisResult> get() {
        Optional<List<AutoAnalysisResult>> cached = cacheRepository.get(
                CacheKey.PROFILE_AUTO_ANALYSIS, ANALYSIS_RESULT_TYPE);

        if (cached.isPresent()) {
            return cached.get();
        } else {
            List<AutoAnalysisResult> analysis = autoAnalysisProvider.get();
            cacheRepository.insert(CacheKey.PROFILE_AUTO_ANALYSIS, analysis);
            return analysis;
        }
    }
}
