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

package pbouda.jeffrey.local.core.web.controllers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import pbouda.jeffrey.local.core.web.ProfileManagerResolver;
import pbouda.jeffrey.profile.manager.custom.HttpManager;
import pbouda.jeffrey.profile.manager.custom.model.http.HttpOverviewData;
import pbouda.jeffrey.profile.manager.custom.model.http.HttpSingleUriData;

import java.net.URLDecoder;

import static java.nio.charset.StandardCharsets.UTF_8;

@RequestMapping({
        "/api/internal/profiles/{profileId}/http/overview",
        "/api/internal/quick-analysis/profiles/{profileId}/http/overview",
        "/api/internal/workspaces/{workspaceId}/projects/{projectId}/profiles/{profileId}/http/overview"
})
@ResponseBody
public class HttpOverviewController {

    private static final Logger LOG = LoggerFactory.getLogger(HttpOverviewController.class);

    private final ProfileManagerResolver resolver;

    public HttpOverviewController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public HttpOverviewData overviewData(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching HTTP overview");
        return mgr(profileId).overviewData();
    }

    @GetMapping("/single")
    public HttpSingleUriData singleUriData(
            @PathVariable("profileId") String profileId,
            @RequestParam("uri") String uri) {
        LOG.debug("Fetching HTTP single URI data: uri={}", uri);
        String decoded = URLDecoder.decode(uri, UTF_8);
        HttpOverviewData data = mgr(profileId).overviewData(decoded);
        return new HttpSingleUriData(
                data.header(),
                data.uris().getFirst(),
                data.statusCodes(),
                data.methods(),
                data.slowRequests(),
                data.responseTimeSerie(),
                data.requestCountSerie());
    }

    private HttpManager mgr(String profileId) {
        return resolver.resolve(profileId).custom().httpManager();
    }
}
