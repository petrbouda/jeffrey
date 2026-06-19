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

package cafe.jeffrey.recordings.core.manager;

import cafe.jeffrey.shared.common.model.Recording;

/**
 * Optional SPI invoked when a recording is being deleted, so deployments that attach analysis
 * profiles to recordings can clean those up. {@code recordings-core} itself never touches profiles;
 * the {@link #NOOP} default is used by deployments (e.g. the analyst) that have no profiles.
 */
@FunctionalInterface
public interface RecordingProfileCleanup {

    void onRecordingDeleted(Recording recording);

    RecordingProfileCleanup NOOP = recording -> {
    };
}
