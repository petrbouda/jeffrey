/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.manager.registry.ProfileManagerFactoryRegistry;
import pbouda.jeffrey.provider.platform.repository.ProfileRepository;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.ProfileInfo;

import java.nio.file.Path;

public class ProfileManagerImpl implements ProfileManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileManagerImpl.class);

    private final ProfileInfo profileInfo;
    private final ProfileRepository profileRepository;
    private final ProfileManagerFactoryRegistry registry;
    private final JeffreyDirs jeffreyDirs;

    public ProfileManagerImpl(
            ProfileInfo profileInfo,
            ProfileRepository profileRepository,
            ProfileManagerFactoryRegistry registry,
            JeffreyDirs jeffreyDirs) {

        this.profileInfo = profileInfo;
        this.profileRepository = profileRepository;
        this.registry = registry;
        this.jeffreyDirs = jeffreyDirs;
    }

    @Override
    public ProfileInfo info() {
        return profileInfo;
    }

    @Override
    public ProfileConfigurationManager profileConfigurationManager() {
        return registry.configuration().apply(profileInfo);
    }

    @Override
    public AutoAnalysisManager autoAnalysisManager() {
        return registry.analysis().autoAnalysis().apply(profileInfo);
    }

    @Override
    public FlamegraphManager flamegraphManager() {
        return registry.visualization().flamegraph().apply(profileInfo);
    }

    @Override
    public FlamegraphManager diffFlamegraphManager(ProfileManager secondaryManager) {
        return registry.visualization().flamegraphDiff().apply(profileInfo, secondaryManager.info());
    }

    @Override
    public SubSecondManager subSecondManager() {
        return registry.visualization().subSecond().apply(profileInfo);
    }

    @Override
    public TimeseriesManager timeseriesManager() {
        return registry.visualization().timeseries().apply(profileInfo);
    }

    @Override
    public TimeseriesManager diffTimeseriesManager(ProfileManager secondaryManager) {
        return registry.visualization().timeseriesDiff().apply(profileInfo, secondaryManager.info());
    }

    @Override
    public EventViewerManager eventViewerManager() {
        return registry.analysis().eventViewer().apply(profileInfo);
    }

    @Override
    public ThreadManager threadManager() {
        return registry.jvmInsight().thread().apply(profileInfo);
    }

    @Override
    public JITCompilationManager jitCompilationManager() {
        return registry.jvmInsight().jitCompilation().apply(profileInfo);
    }

    @Override
    public GuardianManager guardianManager() {
        return registry.analysis().guardian().apply(profileInfo);
    }

    @Override
    public AdditionalFilesManager additionalFilesManager() {
        return registry.additionalFiles().apply(profileInfo);
    }

    @Override
    public GarbageCollectionManager gcManager() {
        return registry.jvmInsight().gc().apply(profileInfo);
    }

    @Override
    public ContainerManager containerManager() {
        return registry.jvmInsight().container().apply(profileInfo);
    }

    @Override
    public HeapMemoryManager heapMemoryManager() {
        return registry.jvmInsight().heapMemory().apply(profileInfo);
    }

    @Override
    public HeapDumpManager heapDumpManager() {
        return registry.jvmInsight().heapDump().apply(profileInfo);
    }

    @Override
    public ProfileFeaturesManager featuresManager() {
        return registry.features().apply(profileInfo);
    }

    @Override
    public ProfileCustomManager custom() {
        return registry.custom().apply(this);
    }

    @Override
    public ProfileInfo updateName(String name) {
        ProfileInfo updatedProfile = this.profileRepository.update(name);

        LOG.info("Profile updated: project_id={} profile_id={} old_name={} new_name={}",
                profileInfo.projectId(), profileInfo.id(), profileInfo.name(), name);

        return updatedProfile;
    }

    @Override
    public void delete() {
        // 1. Delete profile metadata from platform database
        this.profileRepository.delete();
        // 2. Delete profile database and directory
        Path profileDirectory = jeffreyDirs.profileDirectory(profileInfo.id());
        FileSystemUtils.removeDirectory(profileDirectory);

        LOG.info("Profile deleted: project_id={} profile_id={} name={}",
                profileInfo.projectId(), profileInfo.id(), profileInfo.name());
    }
}
