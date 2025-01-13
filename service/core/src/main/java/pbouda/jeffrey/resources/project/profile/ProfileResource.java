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

package pbouda.jeffrey.resources.project.profile;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import pbouda.jeffrey.common.model.profile.ProfileInfo;
import pbouda.jeffrey.manager.ProfileManager;

public class ProfileResource {

    private final ProfileManager profileManager;

    public ProfileResource(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    @Path("/analysis")
    public AutoAnalysisResource autoAnalysisResource() {
        return new AutoAnalysisResource(profileManager.autoAnalysisManager());
    }

    @Path("/viewer")
    public EventViewerResource eventViewerResource() {
        return new EventViewerResource(profileManager.eventViewerManager());
    }

    @Path("/flamegraph")
    public FlamegraphResource flamegraphResource() {
        return new FlamegraphResource(profileManager.flamegraphManager());
    }

    @Path("/guardian")
    public GuardianResource guardianResource() {
        return new GuardianResource(profileManager.guardianManager());
    }

    @Path("/information")
    public InformationResource informationResource() {
        return new InformationResource(profileManager.profileConfigurationManager());
    }

    @Path("/thread")
    public ThreadResource threadResource() {
        return new ThreadResource(profileManager.threadManager());
    }

    @Path("/subsecond")
    public SubSecondResource subSecondResource() {
        return new SubSecondResource(profileManager.subSecondManager());
    }

    @Path("/timeseries")
    public TimeseriesResource timeseriesResource() {
        return new TimeseriesResource(profileManager.timeseriesManager());
    }

    @GET
    public ProfileInfo getProfileInfo() {
        return profileManager.info();
    }

    @DELETE
    public void deleteProfile() {
        profileManager.cleanup();
    }
}
