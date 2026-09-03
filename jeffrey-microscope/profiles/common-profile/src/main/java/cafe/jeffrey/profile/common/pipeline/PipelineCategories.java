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

package cafe.jeffrey.profile.common.pipeline;

import cafe.jeffrey.shared.notification.NotificationCategory;

import java.util.Map;

/**
 * What each pipeline's notifications are about.
 * <p>
 * Read from the pipeline id rather than carried on {@link PipelineDefinition}, because a definition
 * is declared next to its stage ids by code that has no interest in notifications, and threading a
 * category through it would make every pipeline restate something its id already says.
 * <p>
 * A map rather than a switch so a pipeline added elsewhere is one entry here, and shared by the run
 * and the registry so a stage failure and the run failure that follows it cannot disagree.
 */
final class PipelineCategories {

    private static final Map<String, NotificationCategory> BY_PIPELINE_ID = Map.of(
            "profile-init", NotificationCategory.PROFILE,
            "heap-dump-init", NotificationCategory.HEAP_DUMP);

    private PipelineCategories() {
    }

    /**
     * The category for a pipeline id, defaulting to {@code PROFILE} for one nothing maps.
     *
     * <p>The default is honest rather than a placeholder: every pipeline here runs for exactly one
     * profile, so a pipeline this file has not caught up with is still about a profile.
     */
    static NotificationCategory of(String pipelineId) {
        return BY_PIPELINE_ID.getOrDefault(pipelineId, NotificationCategory.PROFILE);
    }
}
