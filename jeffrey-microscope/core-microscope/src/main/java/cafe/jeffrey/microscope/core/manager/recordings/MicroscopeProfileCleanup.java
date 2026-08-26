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
import cafe.jeffrey.shared.notification.NotificationCategory;
import cafe.jeffrey.shared.notification.NotificationType;
import cafe.jeffrey.shared.notification.Notifications;
import cafe.jeffrey.jfr.events.notification.Severity;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Microscope's profile-aware {@link RecordingProfileCleanup}: when a recording that has an analysis
 * profile is deleted, its profile database and on-disk directory are removed too. Also exposes
 * {@link #deleteProfile(String)} for the standalone "delete profile, keep recording" operation.
 */
public class MicroscopeProfileCleanup implements RecordingProfileCleanup {

    private static final Logger LOG = LoggerFactory.getLogger(MicroscopeProfileCleanup.class);

    /** Why the profile went: asked for on its own, or taken along by its recording. */
    private static final String CAUSE_REQUESTED = "REQUESTED";
    private static final String CAUSE_RECORDING_DELETED = "RECORDING_DELETED";

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
            deleteProfile(recording.profileId(), recording.id());
        }
    }

    /**
     * Deletes a profile the caller asked for by itself, with the recording left in place.
     */
    public void deleteProfile(String profileId) {
        deleteProfile(profileId, null);
    }

    /**
     * Deletes a profile's database row and its whole directory.
     *
     * @param recordingId the recording whose deletion took this profile with it, or {@code null} when
     *                    the profile was deleted on its own. It is only carried to answer the one
     *                    question a reader has when a profile is gone -- whether the recording went
     *                    too, or whether it is still there to analyse again
     */
    public void deleteProfile(String profileId, String recordingId) {
        Path profileDir = jeffreyDirs.profileDir(profileId);

        localCoreRepositories.newProfileRepository(profileId).delete();

        if (Files.exists(profileDir)) {
            FileSystemUtils.removeDirectory(profileDir);
        }

        LOG.info("Profile deleted: profileId={}", profileId);

        Notifications.of(NotificationType.PROFILE_DELETED)
                .attribute("profileId", profileId)
                .attribute("recordingId", recordingId)
                .attribute("cause", recordingId == null ? CAUSE_REQUESTED : CAUSE_RECORDING_DELETED)
                .emit();
    }
}
