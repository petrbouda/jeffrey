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
import cafe.jeffrey.profile.manager.custom.GrpcManager;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcOverviewData;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcServiceDetailData;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcTrafficData;

import java.net.URLDecoder;

import static java.nio.charset.StandardCharsets.UTF_8;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/grpc/overview")
public class GrpcOverviewController {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcOverviewController.class);

    private final ProfileManagerResolver resolver;

    public GrpcOverviewController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public GrpcOverviewData overviewData(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "mode", required = false) String mode) {
        LOG.debug("Fetching gRPC overview");
        return mgr(profileId, mode).overviewData();
    }

    @GetMapping("/service")
    public GrpcServiceDetailData serviceDetail(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam("service") String service) {
        LOG.debug("Fetching gRPC service detail: service={}", service);
        return mgr(profileId, mode).serviceDetailData(URLDecoder.decode(service, UTF_8));
    }

    @GetMapping("/traffic")
    public GrpcTrafficData traffic(@PathVariable("profileId") String profileId,
            @RequestParam(value = "mode", required = false) String mode) {
        LOG.debug("Fetching gRPC traffic data");
        return mgr(profileId, mode).trafficData();
    }

    @GetMapping("/traffic/service")
    public GrpcTrafficData trafficByService(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam("service") String service) {
        LOG.debug("Fetching gRPC traffic data for service: service={}", service);
        return mgr(profileId, mode).trafficData(URLDecoder.decode(service, UTF_8));
    }

    private GrpcManager mgr(String profileId, String mode) {
        return resolver.resolve(profileId).custom().grpcManager(ExchangeDirection.from(mode));
    }
}
