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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.profile.manager.AutoAnalysisManager;

/**
 * Whether the auto-analysis rules have run for a profile: the one vocabulary every answer that
 * reports the rules' findings uses, so "not run" is never read as "ran and found nothing".
 * <p>
 * Asked of {@code AutoAnalysisManager.isComputed()} and {@code canGenerate()}, never inferred from an
 * empty result: a run that flagged nothing caches an empty list, which is an answer.
 */
public enum AutoAnalysisStatus {

    /** The rules ran and their result is cached; the findings are theirs, even when there are none. */
    COMPUTED,

    /** The rules have not run and can: {@code jvm_autoAnalysis} with {@code compute} runs them. */
    NOT_COMPUTED,

    /**
     * The rules have not run and cannot: no JFR recording file is available for this profile, because
     * it was removed or the profile was not imported from a JFR recording (a pprof or OTLP sample set,
     * a heap dump).
     */
    CANNOT_COMPUTE;

    /** Where the rules stand for the profile this manager belongs to; asks, never runs them. */
    public static AutoAnalysisStatus of(AutoAnalysisManager autoAnalysis) {
        if (autoAnalysis.isComputed()) {
            return COMPUTED;
        }
        return autoAnalysis.canGenerate() ? NOT_COMPUTED : CANNOT_COMPUTE;
    }
}
