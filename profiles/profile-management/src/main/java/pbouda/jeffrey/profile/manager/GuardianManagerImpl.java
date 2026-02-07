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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.guardian.GuardianProvider;
import pbouda.jeffrey.profile.guardian.GuardianResult;
import pbouda.jeffrey.profile.guardian.guard.GuardAnalysisResult;

import java.time.Duration;
import java.util.List;

public class GuardianManagerImpl implements GuardianManager {

    private static final Logger LOG = LoggerFactory.getLogger(GuardianManagerImpl.class);

    private final GuardianProvider guardianProvider;

    public GuardianManagerImpl(GuardianProvider guardianProvider) {
        this.guardianProvider = guardianProvider;
    }

    @Override
    public List<GuardAnalysisResult> guardResults() {
        LOG.debug("Running guardian analysis");
        long startTime = System.nanoTime();
        List<GuardAnalysisResult> results = guardianProvider.get().stream()
                .map(GuardianResult::analysisItem)
                .toList();
        LOG.debug("Guardian analysis completed: resultCount={} durationMs={}", results.size(), Duration.ofNanos(System.nanoTime() - startTime).toMillis());
        return results;
    }
}
