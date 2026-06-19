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

package cafe.jeffrey.microscope.core.manager.recordings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.recordings.core.manager.RecordingProfileCleanup;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.Recording;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Microscope's profile-aware {@link RecordingProfileCleanup}: when a recording that has an analysis
 * profile is deleted, its profile database and on-disk directory are removed too. Also exposes
 * {@link #deleteProfile(String)} for the standalone "delete profile, keep recording" operation.
 */
public class MicroscopeProfileCleanup implements RecordingProfileCleanup {

    private static final Logger LOG = LoggerFactory.getLogger(MicroscopeProfileCleanup.class);

    private final MicroscopeJeffreyDirs jeffreyDirs;
    private final MicroscopeCoreRepositories localCoreRepositories;

    public MicroscopeProfileCleanup(
            MicroscopeJeffreyDirs jeffreyDirs,
            MicroscopeCoreRepositories localCoreRepositories) {

        this.jeffreyDirs = jeffreyDirs;
        this.localCoreRepositories = localCoreRepositories;
    }

    @Override
    public void onRecordingDeleted(Recording recording) {
        if (recording.hasProfile()) {
            deleteProfile(recording.profileId());
        }
    }

    public void deleteProfile(String profileId) {
        Path profileDir = jeffreyDirs.profileDir(profileId);

        localCoreRepositories.newProfileRepository(profileId).delete();

        if (Files.exists(profileDir)) {
            FileSystemUtils.removeDirectory(profileDir);
        }

        LOG.info("Profile deleted: profileId={}", profileId);
    }
}
