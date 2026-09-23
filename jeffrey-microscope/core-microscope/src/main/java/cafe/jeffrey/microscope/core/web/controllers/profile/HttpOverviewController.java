/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.web.controllers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.custom.ExchangeDirection;
import cafe.jeffrey.profile.manager.custom.HttpManager;
import cafe.jeffrey.profile.manager.custom.model.http.HttpOverviewData;
import cafe.jeffrey.profile.manager.custom.model.http.HttpSingleUriData;

import java.net.URLDecoder;

import static java.nio.charset.StandardCharsets.UTF_8;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/http/overview")
public class HttpOverviewController {

    private static final Logger LOG = LoggerFactory.getLogger(HttpOverviewController.class);

    private final ProfileManagerResolver resolver;

    public HttpOverviewController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public HttpOverviewData overviewData(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "mode", required = false) String mode) {
        LOG.debug("Fetching HTTP overview");
        return mgr(profileId, mode).overviewData();
    }

    @GetMapping("/single")
    public HttpSingleUriData singleUriData(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam("uri") String uri) {
        LOG.debug("Fetching HTTP single URI data: uri={}", uri);
        String decoded = URLDecoder.decode(uri, UTF_8);
        HttpOverviewData data = mgr(profileId, mode).overviewData(decoded);
        return new HttpSingleUriData(
                data.header(),
                data.uris().getFirst(),
                data.statusCodes(),
                data.methods(),
                data.slowRequests(),
                data.responseTimeSerie(),
                data.requestCountSerie());
    }

    private HttpManager mgr(String profileId, String mode) {
        return resolver.resolve(profileId).custom().httpManager(ExchangeDirection.from(mode));
    }
}
