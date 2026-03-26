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

package pbouda.jeffrey.profile.resources.custom;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.manager.custom.GrpcManager;
import pbouda.jeffrey.profile.manager.custom.model.grpc.GrpcOverviewData;
import pbouda.jeffrey.profile.manager.custom.model.grpc.GrpcServiceDetailData;
import pbouda.jeffrey.profile.manager.custom.model.grpc.GrpcTrafficData;

import java.net.URLDecoder;

import static java.nio.charset.StandardCharsets.UTF_8;

public class GrpcOverviewResource {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcOverviewResource.class);

    private final GrpcManager grpcManager;

    public GrpcOverviewResource(GrpcManager grpcManager) {
        this.grpcManager = grpcManager;
    }

    @GET
    public GrpcOverviewData overviewData() {
        LOG.debug("Fetching gRPC overview");
        return grpcManager.overviewData();
    }

    @GET
    @Path("service")
    public GrpcServiceDetailData serviceDetail(@QueryParam("service") String service) {
        LOG.debug("Fetching gRPC service detail: service={}", service);
        String decoded = URLDecoder.decode(service, UTF_8);
        return grpcManager.serviceDetailData(decoded);
    }

    @GET
    @Path("traffic")
    public GrpcTrafficData traffic() {
        LOG.debug("Fetching gRPC traffic data");
        return grpcManager.trafficData();
    }

    @GET
    @Path("traffic/service")
    public GrpcTrafficData trafficByService(@QueryParam("service") String service) {
        LOG.debug("Fetching gRPC traffic data for service: service={}", service);
        String decoded = URLDecoder.decode(service, UTF_8);
        return grpcManager.trafficData(decoded);
    }
}
