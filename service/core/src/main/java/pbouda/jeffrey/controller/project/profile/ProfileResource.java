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

package pbouda.jeffrey.controller.project.profile;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import pbouda.jeffrey.manager.ProfileManager;

public class ProfileResource {

    private final ProfileManager primaryProfileManager;
    private final ProfileManager secondaryProfileManager;

    public ProfileResource(ProfileManager primaryProfileManager) {
        this(primaryProfileManager, null);
    }

    public ProfileResource(ProfileManager primaryProfileManager, ProfileManager secondaryProfileManager) {
        this.primaryProfileManager = primaryProfileManager;
        this.secondaryProfileManager = secondaryProfileManager;
    }

    @Path("/analysis")
    public AutoAnalysisResource autoAnalysisResource() {
        return new AutoAnalysisResource(primaryProfileManager.autoAnalysisManager());
    }

    @Path("/viewer")
    public EventViewerResource eventViewerResource() {
        return new EventViewerResource(primaryProfileManager.eventViewerManager());
    }

    @Path("/flamegraph")
    public FlamegraphResource flamegraphResource() {
        return new FlamegraphResource(primaryProfileManager.flamegraphManager());
    }

    @Path("/differential-flamegraph")
    public FlamegraphDiffResource flamegraphDiffResource() {
        return new FlamegraphDiffResource(primaryProfileManager.diffFlamegraphManager(secondaryProfileManager));
    }

    @Path("/guardian")
    public GuardianResource guardianResource() {
        return new GuardianResource(primaryProfileManager.guardianManager());
    }

    @Path("/information")
    public InformationResource informationResource() {
        return new InformationResource(primaryProfileManager.informationManager());
    }

    @Path("/subsecond")
    public SubSecondResource subSecondResource() {
        return new SubSecondResource(primaryProfileManager.subSecondManager());
    }

    @Path("/timeseries")
    public TimeseriesResource timeseriesResource() {
        return new TimeseriesResource(primaryProfileManager.timeseriesManager());
    }

    @Path("/differential-timeseries")
    public TimeseriesResource timeseriesDiffResource() {
        return new TimeseriesResource(primaryProfileManager.diffTimeseriesManager(secondaryProfileManager));
    }

    @DELETE
    public void deleteProfile() {
        primaryProfileManager.cleanup();
    }
}
