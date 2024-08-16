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

import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.repository.model.ProfileInfo;

public class DbBasedProfileManager implements ProfileManager {

    private final ProfileInfo profileInfo;
    private final WorkingDirs workingDirs;
    private final GraphManager.FlamegraphFactory flamegraphManagerFactory;
    private final GraphManager.DiffgraphFactory diffgraphManagerFactory;
    private final SubSecondManager.Factory subSecondManagerFactory;
    private final TimeseriesManager.Factory timeseriesManagerFactory;
    private final EventViewerManager.Factory eventViewerFactory;
    private final ProfileInfoManager profileInfoManager;
    private final ProfileAutoAnalysisManager profileAutoAnalysisManager;

    public DbBasedProfileManager(
            ProfileInfo profileInfo,
            WorkingDirs workingDirs,
            GraphManager.FlamegraphFactory flamegraphManagerFactory,
            GraphManager.DiffgraphFactory diffgraphManagerFactory,
            SubSecondManager.Factory subSecondManagerFactory,
            TimeseriesManager.Factory timeseriesManagerFactory,
            EventViewerManager.Factory eventViewerFactory,
            ProfileInfoManager profileInfoManager,
            ProfileAutoAnalysisManager profileAutoAnalysisManager) {

        this.profileInfo = profileInfo;
        this.workingDirs = workingDirs;
        this.flamegraphManagerFactory = flamegraphManagerFactory;
        this.diffgraphManagerFactory = diffgraphManagerFactory;
        this.subSecondManagerFactory = subSecondManagerFactory;
        this.timeseriesManagerFactory = timeseriesManagerFactory;
        this.eventViewerFactory = eventViewerFactory;
        this.profileInfoManager = profileInfoManager;
        this.profileAutoAnalysisManager = profileAutoAnalysisManager;
    }

    @Override
    public ProfileInfo info() {
        return profileInfo;
    }

    @Override
    public ProfileInfoManager profileInfoManager() {
        return profileInfoManager;
    }

    @Override
    public ProfileAutoAnalysisManager profileAutoAnalysisManager() {
        return profileAutoAnalysisManager;
    }

    @Override
    public GraphManager flamegraphManager() {
        return flamegraphManagerFactory.apply(profileInfo);
    }

    @Override
    public GraphManager diffgraphManager(ProfileManager secondaryManager) {
        return diffgraphManagerFactory.apply(profileInfo, secondaryManager.info());
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
    public EventViewerManager eventViewerManager() {
        return eventViewerFactory.apply(profileInfo);
    }

    @Override
    public void cleanup() {
        workingDirs.deleteProfile(profileInfo.id());
    }
}
