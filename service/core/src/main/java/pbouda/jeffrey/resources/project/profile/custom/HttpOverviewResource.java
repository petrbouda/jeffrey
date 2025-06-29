/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.resources.project.profile.custom;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import pbouda.jeffrey.manager.custom.HttpManager;
import pbouda.jeffrey.manager.custom.model.http.HttpOverviewData;
import pbouda.jeffrey.manager.custom.model.http.HttpSingleUriData;

import java.net.URLDecoder;

import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpOverviewResource {

    private final HttpManager httpManager;

    public HttpOverviewResource(HttpManager httpManager) {
        this.httpManager = httpManager;
    }

    @GET
    public HttpOverviewData overviewData() {
        return httpManager.overviewData();
    }

    @GET
    @Path("single")
    public HttpSingleUriData singleUriData(@QueryParam("uri") String uri) {
        String decoded = URLDecoder.decode(uri, UTF_8);
        HttpOverviewData httpOverviewData = httpManager.overviewData(decoded);
        return toSingleUriData(httpOverviewData);
    }

    private static HttpSingleUriData toSingleUriData(HttpOverviewData overviewData) {
        return new HttpSingleUriData(
                overviewData.header(),
                overviewData.uris().getFirst(),
                overviewData.statusCodes(),
                overviewData.methods(),
                overviewData.slowRequests(),
                overviewData.responseTimeSerie(),
                overviewData.requestCountSerie()
        );
    }
}
