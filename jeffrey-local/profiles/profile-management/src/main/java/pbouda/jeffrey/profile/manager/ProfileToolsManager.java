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

package pbouda.jeffrey.profile.manager;

import pbouda.jeffrey.provider.profile.repository.ProfileFrameRepository.FrameRenamePreview;
import pbouda.jeffrey.shared.common.model.ProfileInfo;

import java.util.List;
import java.util.function.Function;

public interface ProfileToolsManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, ProfileToolsManager> {
    }

    record RenameRequest(String search, String replacement) {
    }

    record RenamePreviewResult(int affectedFrames, List<FrameRenamePreview> samples) {
    }

    record RenameResult(int renamedFrames) {
    }

    RenamePreviewResult previewRename(RenameRequest request);

    RenameResult executeRename(RenameRequest request);
}
