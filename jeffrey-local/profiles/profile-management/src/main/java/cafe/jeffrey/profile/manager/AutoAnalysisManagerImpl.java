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

package cafe.jeffrey.profile.manager;

import tools.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.parser.data.AutoAnalysisDataProvider;
import cafe.jeffrey.provider.profile.repository.ProfileCacheRepository;
import cafe.jeffrey.shared.common.CacheKey;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class AutoAnalysisManagerImpl implements AutoAnalysisManager {

    private static final Logger LOG = LoggerFactory.getLogger(AutoAnalysisManagerImpl.class);

    private static final TypeReference<List<AutoAnalysisResult>> ANALYSIS_RESULT_TYPE =
            new TypeReference<List<AutoAnalysisResult>>() {
            };

    private final ProfileCacheRepository cacheRepository;
    private final Supplier<Optional<Path>> recordingPathResolver;

    public AutoAnalysisManagerImpl(
            ProfileCacheRepository cacheRepository,
            Supplier<Optional<Path>> recordingPathResolver) {

        this.cacheRepository = cacheRepository;
        this.recordingPathResolver = recordingPathResolver;
    }

    @Override
    public List<AutoAnalysisResult> analysisResults() {
        return cacheRepository.get(CacheKey.PROFILE_AUTO_ANALYSIS, ANALYSIS_RESULT_TYPE)
                .orElse(List.of());
    }

    @Override
    public List<AutoAnalysisResult> generate() {
        Path recordingPath = recordingPathResolver.get()
                .orElseThrow(() -> new IllegalStateException("Recording file not found"));

        LOG.info("Generating auto analysis on-demand: recording={}", recordingPath);

        List<AutoAnalysisResult> results = AutoAnalysisDataProvider.generate(recordingPath).stream()
                .sorted(Comparator.comparing(a -> a.severity().order()))
                .toList();

        cacheRepository.put(CacheKey.PROFILE_AUTO_ANALYSIS, results);
        LOG.info("Auto analysis completed and cached: results={}", results.size());

        return results;
    }
}
