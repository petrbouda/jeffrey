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

package cafe.jeffrey.profile.tools.collapse;

import cafe.jeffrey.provider.profile.api.ProfileToolsRepository.FrameSample;
import cafe.jeffrey.shared.common.model.ProfileInfo;

import java.util.List;
import java.util.function.Function;

public interface CollapseFramesManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, CollapseFramesManager> {
    }

    record CollapseRequest(List<String> patterns, String label) {
    }

    record CollapsePreviewResult(int matchingFrames, int affectedStacktraces, List<FrameSample> samples) {
    }

    record CollapseApplyResult(int affectedStacktraces, int mergedStacktraces) {
    }

    CollapsePreviewResult preview(CollapseRequest request);

    CollapseApplyResult execute(CollapseRequest request);
}
