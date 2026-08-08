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

package cafe.jeffrey.microscope.core.manager;

import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.manager.server.HubManager;
import cafe.jeffrey.microscope.core.manager.server.HubsManager;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Recreates a profile from its original recording. Used when an existing profile database was
 * created by an older Jeffrey version (schema outdated): the old profile is deleted and the
 * recording is re-analyzed, producing a fresh profile with the current schema. The new profile
 * appears in the profile list with the regular "analyzing" state while it is parsed.
 */
public class ProfileRecreationManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileRecreationManager.class);

    private final HubsManager remoteServersManager;
    private final RecordingsManager recordingsManager;
    private final MicroscopeCoreRepositories localCoreRepositories;

    public ProfileRecreationManager(
            HubsManager remoteServersManager,
            RecordingsManager recordingsManager,
            MicroscopeCoreRepositories localCoreRepositories) {
        this.remoteServersManager = remoteServersManager;
        this.recordingsManager = recordingsManager;
        this.localCoreRepositories = localCoreRepositories;
    }

    public void recreate(String profileId) {
        if (recordingsManager != null) {
            Optional<ProfileManager> quickProfile = recordingsManager.profile(profileId);
            if (quickProfile.isPresent()) {
                recreateQuickAnalysisProfile(profileId, quickProfile.get().info());
                return;
            }
        }
        recreateProjectProfile(profileId);
    }

    private void recreateQuickAnalysisProfile(String profileId, ProfileInfo profileInfo) {
        String recordingId = requireRecordingId(profileId, profileInfo);
        LOG.info("Recreating quick-analysis profile from recording: profile_id={} recording_id={}",
                profileId, recordingId);
        recordingsManager.deleteProfile(recordingId);
        recordingsManager.analyzeRecording(recordingId);
    }

    private void recreateProjectProfile(String profileId) {
        ProfileInfo profileInfo = localCoreRepositories.newProfileRepository(profileId).find()
                .orElseThrow(() -> Exceptions.profileNotFound(profileId));

        ProfilesManager profilesManager = findOwningProfilesManager(profileInfo)
                .orElseThrow(() -> Exceptions.profileNotFound(profileId));
        ProfileManager profileManager = profilesManager.profile(profileId)
                .orElseThrow(() -> Exceptions.profileNotFound(profileId));

        String recordingId = requireRecordingId(profileId, profileInfo);
        LOG.info("Recreating project profile from recording: profile_id={} recording_id={}",
                profileId, recordingId);

        // createProfile verifies the recording (database row + file in storage) synchronously
        // before submitting the asynchronous parse, so the old profile is only deleted once the
        // replacement is on its way.
        try {
            profilesManager.createProfile(recordingId);
        } catch (IllegalArgumentException e) {
            throw Exceptions.recordingNotFound(recordingId);
        }
        profileManager.delete();
    }

    private Optional<ProfilesManager> findOwningProfilesManager(ProfileInfo profileInfo) {
        for (HubManager server : remoteServersManager.findAll()) {
            Optional<ProfilesManager> profilesManager = server.workspace(profileInfo.workspaceId())
                    .flatMap(workspace -> workspace.projectsManager().project(profileInfo.projectId()))
                    .map(ProjectManager::profilesManager);
            if (profilesManager.isPresent()) {
                return profilesManager;
            }
        }
        return Optional.empty();
    }

    private static String requireRecordingId(String profileId, ProfileInfo profileInfo) {
        String recordingId = profileInfo.recordingId();
        if (recordingId == null || recordingId.isBlank()) {
            throw Exceptions.invalidRequest(
                    "Profile cannot be recreated, no original recording: " + profileId);
        }
        return recordingId;
    }
}
