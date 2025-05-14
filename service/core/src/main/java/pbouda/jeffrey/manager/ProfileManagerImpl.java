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

package pbouda.jeffrey.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.provider.api.repository.ProfileRepository;

public class ProfileManagerImpl implements ProfileManager {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileManagerImpl.class);

    private final ProfileInfo profileInfo;
    private final ProfileRepository profileRepository;
    private final FlamegraphManager.Factory flamegraphManagerFactory;
    private final FlamegraphManager.DifferentialFactory flamegraphManagerDiffFactory;
    private final SubSecondManager.Factory subSecondManagerFactory;
    private final TimeseriesManager.Factory timeseriesManagerFactory;
    private final TimeseriesManager.DifferentialFactory timeseriesManagerDiffFactory;
    private final EventViewerManager.Factory eventViewerFactory;
    private final GuardianManager.Factory guardianManagerFactory;
    private final ProfileConfigurationManager.Factory configurationManagerFactory;
    private final AutoAnalysisManager.Factory autoAnalysisManagerFactory;
    private final ThreadManager.Factory threadManagerFactory;
    private final AdditionalFilesManager.Factory additionalFeaturesManagerFactory;

    public ProfileManagerImpl(
            ProfileInfo profileInfo,
            ProfileRepository profileRepository,
            FlamegraphManager.Factory flamegraphManagerFactory,
            FlamegraphManager.DifferentialFactory flamegraphManagerDiffFactory,
            SubSecondManager.Factory subSecondManagerFactory,
            TimeseriesManager.Factory timeseriesManagerFactory,
            TimeseriesManager.DifferentialFactory timeseriesManagerDiffFactory,
            EventViewerManager.Factory eventViewerFactory,
            GuardianManager.Factory guardianManagerFactory,
            ProfileConfigurationManager.Factory configurationManagerFactory,
            AutoAnalysisManager.Factory autoAnalysisManagerFactory,
            ThreadManager.Factory threadManagerFactory,
            AdditionalFilesManager.Factory additionalFeaturesManagerFactory) {

        this.profileInfo = profileInfo;
        this.profileRepository = profileRepository;
        this.flamegraphManagerFactory = flamegraphManagerFactory;
        this.flamegraphManagerDiffFactory = flamegraphManagerDiffFactory;
        this.subSecondManagerFactory = subSecondManagerFactory;
        this.timeseriesManagerFactory = timeseriesManagerFactory;
        this.timeseriesManagerDiffFactory = timeseriesManagerDiffFactory;
        this.eventViewerFactory = eventViewerFactory;
        this.guardianManagerFactory = guardianManagerFactory;
        this.configurationManagerFactory = configurationManagerFactory;
        this.autoAnalysisManagerFactory = autoAnalysisManagerFactory;
        this.threadManagerFactory = threadManagerFactory;
        this.additionalFeaturesManagerFactory = additionalFeaturesManagerFactory;
    }

    @Override
    public ProfileInfo info() {
        return profileInfo;
    }

    @Override
    public ProfileConfigurationManager profileConfigurationManager() {
        return configurationManagerFactory.apply(profileInfo);
    }

    @Override
    public AutoAnalysisManager autoAnalysisManager() {
        return autoAnalysisManagerFactory.apply(profileInfo);
    }

    @Override
    public FlamegraphManager flamegraphManager() {
        return flamegraphManagerFactory.apply(profileInfo);
    }

    @Override
    public FlamegraphManager diffFlamegraphManager(ProfileManager secondaryManager) {
        return flamegraphManagerDiffFactory.apply(profileInfo, secondaryManager.info());
    }

    @Override
    public SubSecondManager subSecondManager() {
        return subSecondManagerFactory.apply(profileInfo);
    }

    @Override
    public TimeseriesManager timeseriesManager() {
        return timeseriesManagerFactory.apply(profileInfo);
    }

    @Override
    public TimeseriesManager diffTimeseriesManager(ProfileManager secondaryManager) {
        return timeseriesManagerDiffFactory.apply(profileInfo, secondaryManager.info());
    }

    @Override
    public EventViewerManager eventViewerManager() {
        return eventViewerFactory.apply(profileInfo);
    }

    @Override
    public ThreadManager threadManager() {
        return threadManagerFactory.apply(profileInfo);
    }

    @Override
    public GuardianManager guardianManager() {
        return guardianManagerFactory.apply(profileInfo);
    }

    @Override
    public AdditionalFilesManager additionalFilesManager() {
        return additionalFeaturesManagerFactory.apply(profileInfo);
    }

    @Override
    public void delete() {
        this.profileRepository.delete();

        LOG.info("Profile deleted: project_id={} profile_id={} name={}",
                profileInfo.projectId(), profileInfo.id(), profileInfo.name());
    }
}
