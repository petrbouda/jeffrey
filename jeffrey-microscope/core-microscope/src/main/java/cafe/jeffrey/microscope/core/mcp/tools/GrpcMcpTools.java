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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.custom.ExchangeDirection;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcHeader;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcLargestCall;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcMethodInfo;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcOverviewData;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcServiceDetailData;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcServiceInfo;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcSizeBucket;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcSlowCall;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcStatusStats;
import cafe.jeffrey.profile.manager.custom.model.grpc.GrpcTrafficData;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The gRPC calls the profiled JVM served: latency, status codes, per-service breakdown, and - on its
 * own tool - message sizes, which is the dimension gRPC gets wrong more often than time.
 * <p>
 * Both directions are answerable, like {@link HttpMcpTools}: SERVER is what this application was
 * asked to do, CLIENT what it asked of somebody else. The chart series are dropped from every answer.
 */
public class GrpcMcpTools {

    private static final String OVERVIEW_VIEW = "technologies/grpc/overview";
    private static final String SERVICES_VIEW = "technologies/grpc/services";
    private static final String TRAFFIC_VIEW = "technologies/grpc/traffic";
    private static final String MODE_PARAM = "mode";
    private static final String SERVICE_PARAM = "service";

    private static final int MAX_SERVICES = 40;

    private static final String NO_GRPC_DATA =
            "This profile holds no %s-side gRPC data: the recording did not capture %s events. That is "
                    + "a profiler-configuration finding worth reporting rather than evidence that the "
                    + "service handles no gRPC in that direction.";

    private static final String STEP_SERVICE =
            "For one service broken down by method, grpc_service takes a name from the services list "
                    + "above.";
    private static final String STEP_TRAFFIC =
            "These are timings. The bytes moved - and an oversized payload, which shows up nowhere in "
                    + "the latency figures until it is already a problem - are in grpc_traffic.";
    private static final String STEP_ERRORS =
            "Some calls failed. What the application said about them is in traces_notifications, and "
                    + "the individual calls are in traces_operations, when this profile carries traces.";
    private static final String STEP_TIMINGS =
            "How long these calls took, rather than how large they were, is in grpc_overview.";

    private static final String NO_SUCH_SERVICE =
            "No calls were recorded for service '%s'. Call grpc_overview and take a name from its "
                    + "services list.";

    private final ProfileManager profileManager;

    public GrpcMcpTools(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    @Tool(description = "The gRPC server dashboard: call count, response-time percentiles, success "
            + "rate and error count, the services ranked by traffic, the status-code breakdown and the "
            + "slowest individual calls. Start here for gRPC latency questions.")
    public String overview(
            @ToolParam(description = "Which side to report on: 'SERVER' for calls this application "
                    + "answered (the default), 'CLIENT' for calls it made to somebody else.")
            String direction) {

        ExchangeDirection side = ExchangeDirection.from(direction);
        if (DashboardFeature.missing(profileManager, feature(side))) {
            return noData(side);
        }

        GrpcOverviewData data = profileManager.custom().grpcManager(side).overviewData();
        return LinkedOutput.json(new GrpcDashboard(
                data.header(),
                trim(data.services()),
                data.statusCodes(),
                data.slowCalls(),
                NextSteps.builder()
                        .add(STEP_SERVICE)
                        .when(data.header().errorCount() > 0, STEP_ERRORS)
                        .add(STEP_TRAFFIC)
                        .build(),
                UiLinks.view(profileId(), OVERVIEW_VIEW, mode(side))));
    }

    @Tool(description = "One gRPC service in detail, broken down by method: percentiles per method, "
            + "the status codes it returned and its slowest calls. Use it after grpc_overview has "
            + "named the service worth looking at.")
    public String service(
            @ToolParam(description = "Service name exactly as recorded, taken from the services list "
                    + "in grpc_overview.")
            String service,
            @ToolParam(description = "Which side to report on: 'SERVER' for calls this application "
                    + "answered (the default), 'CLIENT' for calls it made to somebody else.")
            String direction) {

        ExchangeDirection side = ExchangeDirection.from(direction);
        if (DashboardFeature.missing(profileManager, feature(side))) {
            return noData(side);
        }

        GrpcServiceDetailData data =
                profileManager.custom().grpcManager(side).serviceDetailData(service);
        if (data.methods().isEmpty()) {
            return NO_SUCH_SERVICE.formatted(service);
        }

        return LinkedOutput.json(new GrpcServiceDetail(
                data.header(),
                data.methods(),
                data.statusCodes(),
                data.slowCalls(),
                NextSteps.builder()
                        .when(data.header().errorCount() > 0, STEP_ERRORS)
                        .add(STEP_TRAFFIC)
                        .build(),
                UiLinks.view(profileId(), SERVICES_VIEW, serviceQuery(side, service))));
    }

    @Tool(description = "gRPC message sizes rather than timings: bytes sent and received, average and "
            + "maximum request and response sizes, the size-bucket distribution and the largest "
            + "individual calls. This is where an oversized payload shows up - it will not be visible "
            + "in the latency percentiles until it is already a problem.")
    public String traffic(
            @ToolParam(description = "Which side to report on: 'SERVER' for calls this application "
                    + "answered (the default), 'CLIENT' for calls it made to somebody else.")
            String direction) {

        ExchangeDirection side = ExchangeDirection.from(direction);
        if (DashboardFeature.missing(profileManager, feature(side))) {
            return noData(side);
        }

        GrpcTrafficData data = profileManager.custom().grpcManager(side).trafficData();
        return LinkedOutput.json(new GrpcTraffic(
                data.header(),
                data.sizeBuckets(),
                data.largestCalls(),
                NextSteps.builder().add(STEP_TIMINGS).build(),
                UiLinks.view(profileId(), TRAFFIC_VIEW, mode(side))));
    }

    private static Map<String, String> mode(ExchangeDirection direction) {
        Map<String, String> query = UiLinks.query();
        query.put(MODE_PARAM, direction.name().toLowerCase(Locale.ROOT));
        return query;
    }

    private static Map<String, String> serviceQuery(ExchangeDirection direction, String service) {
        Map<String, String> query = mode(direction);
        query.put(SERVICE_PARAM, service);
        return query;
    }

    private static FeatureType feature(ExchangeDirection direction) {
        return direction == ExchangeDirection.SERVER
                ? FeatureType.GRPC_SERVER_DASHBOARD
                : FeatureType.GRPC_CLIENT_DASHBOARD;
    }

    private static String noData(ExchangeDirection direction) {
        return NO_GRPC_DATA.formatted(
                direction.name().toLowerCase(Locale.ROOT), direction.grpcEventType().code());
    }

    private static List<GrpcServiceInfo> trim(List<GrpcServiceInfo> services) {
        return services.size() <= MAX_SERVICES ? services : services.subList(0, MAX_SERVICES);
    }

    private String profileId() {
        return profileManager.info().id();
    }

    private record GrpcDashboard(
            GrpcHeader header,
            List<GrpcServiceInfo> services,
            List<GrpcStatusStats> statusCodes,
            List<GrpcSlowCall> slowCalls,
            List<String> nextSteps,
            String uiLink) {
    }

    private record GrpcServiceDetail(
            GrpcHeader header,
            List<GrpcMethodInfo> methods,
            List<GrpcStatusStats> statusCodes,
            List<GrpcSlowCall> slowCalls,
            List<String> nextSteps,
            String uiLink) {
    }

    private record GrpcTraffic(
            GrpcHeader header,
            List<GrpcSizeBucket> sizeBuckets,
            List<GrpcLargestCall> largestCalls,
            List<String> nextSteps,
            String uiLink) {
    }
}
