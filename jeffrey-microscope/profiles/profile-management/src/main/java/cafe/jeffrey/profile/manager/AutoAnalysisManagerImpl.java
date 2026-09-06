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
import cafe.jeffrey.provider.profile.api.ProfileCacheRepository;
import cafe.jeffrey.shared.common.CacheKey;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

public class AutoAnalysisManagerImpl implements AutoAnalysisManager {

    private static final Logger LOG = LoggerFactory.getLogger(AutoAnalysisManagerImpl.class);

    private static final TypeReference<List<AutoAnalysisResult>> ANALYSIS_RESULT_TYPE =
            new TypeReference<List<AutoAnalysisResult>>() {
            };

    private final ProfileCacheRepository cacheRepository;
    private final Supplier<Optional<Path>> recordingPathResolver;
    private final Function<Path, List<AutoAnalysisResult>> ruleSet;

    /**
     * Guards the single in-flight run. Held only while a run is being handed out or cleared, never
     * while the rule set is running -- a second caller parks on the future, not on this monitor.
     */
    private final Object inFlightLock = new Object();
    private CompletableFuture<List<AutoAnalysisResult>> inFlight;

    /**
     * @param ruleSet what actually evaluates the rules over a recording file. A collaborator rather
     *                than a static call so the single-flight behaviour below can be tested without a
     *                recording on disk.
     */
    public AutoAnalysisManagerImpl(
            ProfileCacheRepository cacheRepository,
            Supplier<Optional<Path>> recordingPathResolver,
            Function<Path, List<AutoAnalysisResult>> ruleSet) {

        this.cacheRepository = cacheRepository;
        this.recordingPathResolver = recordingPathResolver;
        this.ruleSet = ruleSet;
    }

    @Override
    public List<AutoAnalysisResult> analysisResults() {
        return cacheRepository.get(CacheKey.PROFILE_AUTO_ANALYSIS, ANALYSIS_RESULT_TYPE)
                .orElse(List.of());
    }

    @Override
    public boolean canGenerate() {
        return recordingPathResolver.get().isPresent();
    }

    /**
     * Single-flight on purpose: the run loads the entire recording into the JMC item model, so two
     * concurrent runs of the same profile would hold two copies of it at once. The profile's warm-up
     * starts one when the recording is imported, and the Auto Analysis page's button and the MCP
     * tool's {@code compute} flag can both arrive while it is still going.
     */
    @Override
    public List<AutoAnalysisResult> generate() {
        CompletableFuture<List<AutoAnalysisResult>> run;
        boolean owner = false;

        synchronized (inFlightLock) {
            if (inFlight == null) {
                inFlight = new CompletableFuture<>();
                owner = true;
            }
            run = inFlight;
        }

        if (!owner) {
            LOG.debug("Auto analysis already running, joining it");
            return run.join();
        }

        try {
            List<AutoAnalysisResult> results = runRuleSet();
            run.complete(results);
            return results;
        } catch (RuntimeException | Error e) {
            run.completeExceptionally(e);
            throw e;
        } finally {
            synchronized (inFlightLock) {
                inFlight = null;
            }
        }
    }

    private List<AutoAnalysisResult> runRuleSet() {
        Path recordingPath = recordingPathResolver.get()
                .orElseThrow(() -> new IllegalStateException("Recording file not found"));

        LOG.info("Generating auto analysis: recording={}", recordingPath);

        List<AutoAnalysisResult> results = ruleSet.apply(recordingPath).stream()
                .sorted(Comparator.comparing(a -> a.severity().order()))
                .toList();

        cacheRepository.put(CacheKey.PROFILE_AUTO_ANALYSIS, results);
        LOG.info("Auto analysis completed and cached: results={}", results.size());

        return results;
    }
}
