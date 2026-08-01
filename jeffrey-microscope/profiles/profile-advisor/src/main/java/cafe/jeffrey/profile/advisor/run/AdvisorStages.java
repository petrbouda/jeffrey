/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.profile.advisor.run;

import cafe.jeffrey.profile.common.pipeline.PipelineDefinition;

import java.util.List;

/**
 * The Advisor pipeline: what a run does, in the order it does it.
 *
 * <p>Stage ids are opaque strings the frontend attaches labels to. {@code store} is a stage in its own
 * right rather than a tail-end of {@code verify} because writing the findings and the cross-profile
 * claim index is real work with a real duration, and a stage that is invisible is a stage nobody can
 * tell is slow.</p>
 */
public final class AdvisorStages {

    public static final String PIPELINE_ID = "advisor";

    /** Building the flamegraph prompt, or loading the cached one. */
    public static final String PROMPT = "prompt";

    /** Validating the configured source folder and reading its commit. */
    public static final String SOURCE = "source";

    /** The model reading the source through the read-only tools. */
    public static final String ANALYZE = "analyze";

    /** Grounding the model's claims and checking the patch. */
    public static final String VERIFY = "verify";

    /** Writing the findings and the cross-profile claim index. */
    public static final String STORE = "store";

    public static final PipelineDefinition DEFINITION = new PipelineDefinition(
            PIPELINE_ID, List.of(PROMPT, SOURCE, ANALYZE, VERIFY, STORE));

    private AdvisorStages() {
    }
}
