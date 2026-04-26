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
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.local.core.web.ProfileManagerResolver;
import pbouda.jeffrey.profile.manager.custom.GrpcManager;
import pbouda.jeffrey.profile.manager.custom.model.grpc.GrpcOverviewData;
import pbouda.jeffrey.profile.manager.custom.model.grpc.GrpcServiceDetailData;
import pbouda.jeffrey.profile.manager.custom.model.grpc.GrpcTrafficData;

import java.net.URLDecoder;

import static java.nio.charset.StandardCharsets.UTF_8;

@RestController
@RequestMapping({
        "/api/internal/profiles/{profileId}/grpc/overview",
        "/api/internal/quick-analysis/profiles/{profileId}/grpc/overview",
        "/api/internal/workspaces/{workspaceId}/projects/{projectId}/profiles/{profileId}/grpc/overview"
})
public class GrpcOverviewController {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcOverviewController.class);

    private final ProfileManagerResolver resolver;

    public GrpcOverviewController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public GrpcOverviewData overviewData(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching gRPC overview");
        return mgr(profileId).overviewData();
    }

    @GetMapping("/service")
    public GrpcServiceDetailData serviceDetail(
            @PathVariable("profileId") String profileId,
            @RequestParam("service") String service) {
        LOG.debug("Fetching gRPC service detail: service={}", service);
        return mgr(profileId).serviceDetailData(URLDecoder.decode(service, UTF_8));
    }

    @GetMapping("/traffic")
    public GrpcTrafficData traffic(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching gRPC traffic data");
        return mgr(profileId).trafficData();
    }

    @GetMapping("/traffic/service")
    public GrpcTrafficData trafficByService(
            @PathVariable("profileId") String profileId,
            @RequestParam("service") String service) {
        LOG.debug("Fetching gRPC traffic data for service: service={}", service);
        return mgr(profileId).trafficData(URLDecoder.decode(service, UTF_8));
    }

    private GrpcManager mgr(String profileId) {
        return resolver.resolve(profileId).custom().grpcManager();
    }
}
