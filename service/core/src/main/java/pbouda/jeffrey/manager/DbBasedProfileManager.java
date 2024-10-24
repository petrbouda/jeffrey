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

import pbouda.jeffrey.filesystem.ProfileDirs;
import pbouda.jeffrey.repository.model.ProfileInfo;

public class DbBasedProfileManager implements ProfileManager {

    private final ProfileInfo profileInfo;
    private final ProfileDirs profileDirs;
    private final FlamegraphManager.Factory flamegraphManagerFactory;
    private final FlamegraphManager.DifferentialFactory flamegraphManagerDiffFactory;
    private final SubSecondManager.Factory subSecondManagerFactory;
    private final TimeseriesManager.Factory timeseriesManagerFactory;
    private final TimeseriesManager.DifferentialFactory timeseriesManagerDiffFactory;
    private final EventViewerManager.Factory eventViewerFactory;
    private final GuardianManager.Factory guardianManagerFactory;
    private final InformationManager informationManager;
    private final AutoAnalysisManager autoAnalysisManager;

    public DbBasedProfileManager(
            ProfileInfo profileInfo,
            ProfileDirs profileDirs,
            FlamegraphManager.Factory flamegraphManagerFactory,
            FlamegraphManager.DifferentialFactory flamegraphManagerDiffFactory,
            SubSecondManager.Factory subSecondManagerFactory,
            TimeseriesManager.Factory timeseriesManagerFactory,
            TimeseriesManager.DifferentialFactory timeseriesManagerDiffFactory,
            EventViewerManager.Factory eventViewerFactory,
            GuardianManager.Factory guardianManagerFactory,
            InformationManager informationManager,
            AutoAnalysisManager autoAnalysisManager) {

        this.profileInfo = profileInfo;
        this.profileDirs = profileDirs;
        this.flamegraphManagerFactory = flamegraphManagerFactory;
        this.flamegraphManagerDiffFactory = flamegraphManagerDiffFactory;
        this.subSecondManagerFactory = subSecondManagerFactory;
        this.timeseriesManagerFactory = timeseriesManagerFactory;
        this.timeseriesManagerDiffFactory = timeseriesManagerDiffFactory;
        this.eventViewerFactory = eventViewerFactory;
        this.guardianManagerFactory = guardianManagerFactory;
        this.informationManager = informationManager;
        this.autoAnalysisManager = autoAnalysisManager;
    }

    @Override
    public ProfileInfo info() {
        return profileInfo;
    }

    @Override
    public InformationManager informationManager() {
        return informationManager;
    }

    @Override
    public AutoAnalysisManager autoAnalysisManager() {
        return autoAnalysisManager;
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
    public GuardianManager guardianManager() {
        return guardianManagerFactory.apply(profileInfo);
    }

    @Override
    public void cleanup() {
        profileDirs.delete();
    }
}
