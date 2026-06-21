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

package cafe.jeffrey.performance.analyst.recordings;

import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.ui.workspace.bridge.RecordingProfileInfoProvider;
import cafe.jeffrey.shared.ui.workspace.dto.RecordingResponse;

import java.util.List;
import java.util.Map;

/**
 * Assembles the shared {@link RecordingResponse} list (the exact shape the global recordings UI consumes)
 * from a list of recordings plus their tags, enriched with the deployment's profile info. Shared by the
 * project-scoped and recent-across-projects recording endpoints.
 */
public final class RecordingResponses {

    private RecordingResponses() {
    }

    public static List<RecordingResponse> from(
            List<Recording> recordings,
            Map<String, List<RecordingTag>> tagsByRecording,
            RecordingProfileInfoProvider profileInfoProvider) {

        return recordings.stream()
                .map(recording -> RecordingResponse.from(
                        recording,
                        profileInfoProvider.profileInfo(recording),
                        tagsByRecording.getOrDefault(recording.id(), List.of())))
                .toList();
    }
}
