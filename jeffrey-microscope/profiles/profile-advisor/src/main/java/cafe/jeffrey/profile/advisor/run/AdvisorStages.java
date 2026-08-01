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
 * One event type's advisor run, expressed as a {@link PipelineDefinition} for the shared pipeline
 * machinery.
 *
 * <p>The stage ids are {@link AdvisorStatus#STEPS} by name rather than a second list of strings, and
 * that is deliberate on both sides of the wire: the UI keys its step labels off the same names, so
 * deriving them here means a step cannot be added to the enum and forgotten in the pipeline, or
 * renamed in one place and silently unlabelled in the other.</p>
 */
public final class AdvisorStages {

    public static final String PIPELINE_ID = "advisor";

    public static final List<String> STAGE_IDS =
            AdvisorStatus.STEPS.stream().map(AdvisorStatus::name).toList();

    public static final PipelineDefinition DEFINITION =
            new PipelineDefinition(PIPELINE_ID, STAGE_IDS);

    private AdvisorStages() {
    }
}
