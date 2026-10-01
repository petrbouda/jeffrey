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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
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
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
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

    private static final MicroscopeView OVERVIEW_VIEW = MicroscopeView.GRPC_OVERVIEW;
    private static final MicroscopeView SERVICES_VIEW = MicroscopeView.GRPC_SERVICES;
    private static final MicroscopeView TRAFFIC_VIEW = MicroscopeView.GRPC_TRAFFIC;
    private static final String MODE_PARAM = "mode";
    private static final String SERVICE_PARAM = "service";

    private static final int MAX_SERVICES = 40;

    private static final String NO_GRPC_DATA =
            "This profile holds no %s-side gRPC data: the recording did not capture %s events. That is "
                    + "a profiler-configuration finding worth reporting rather than evidence that the "
                    + "service handles no gRPC in that direction.";

    private static final String SERVICE_WHY = "the busiest service broken down by method";
    private static final String TRAFFIC_WHY =
            "the bytes moved - an oversized payload shows up nowhere in the latency figures until it is a problem";
    private static final String TIMINGS_WHY = "how long these calls took, rather than how large they were";
    private static final String OTHER_SIDE_WHY =
            "the other direction - what this application called out to, or what it served";
    private static final String ERRORS_WHY = "what the application said about the failed calls, when this profile carries traces";

    private static final String NO_SUCH_SERVICE =
            "No calls were recorded for service '%s'. Call grpc_overview and take a name from its "
                    + "services list.";

    private static final String NO_SERVICE_RECOVERY =
            "Call grpc_overview and take a name from its services list.";

    private final ProfileManager profileManager;
    private final AdvertisedFamilies advertised;

    public GrpcMcpTools(ProfileManager profileManager, AdvertisedFamilies advertised) {
        this.profileManager = profileManager;
        this.advertised = advertised;
    }

    @Tool(description = "Returns the gRPC server dashboard: call count, response-time percentiles in "
            + "nanoseconds, success rate and error count, the services ranked by traffic (the "
            + "busiest 40, with omittedServices counting the rest), the status-code breakdown and "
            + "the slowest individual calls with their UTC epoch-millisecond instant. Answers gRPC "
            + "latency questions. status NOT_RECORDED: the recording did not capture that "
            + "direction's exchange events.")
    @McpOutputSchema(GrpcDashboard.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult overview(
            @ToolParam(required = false, description = "Which side to report on: SERVER for calls this application "
                    + "answered (the default), CLIENT for calls it made to somebody else.")
            ExchangeDirection direction) {

        ExchangeDirection side = direction == null ? ExchangeDirection.SERVER : direction;
        String uiLink = UiLinks.view(profileId(), OVERVIEW_VIEW, mode(side));
        if (notRecorded(side)) {
            return McpToolResult.of(new GrpcDashboard(DashboardStatus.NOT_RECORDED, noData(side), profileId(), side,
                    null, List.of(), null, List.of(), List.of(), notRecordedFollowUp(side), uiLink));
        }

        GrpcOverviewData data = profileManager.custom().grpcManager(side).overviewData();
        List<GrpcServiceInfo> shown = ToolArguments.firstOf(data.services(), MAX_SERVICES);
        NextSteps.Builder steps = NextSteps.builder(advertised);
        if (!shown.isEmpty()) {
            steps.next(call(FollowUpCalls.GRPC_SERVICE)
                    .with(FollowUpCalls.SERVICE, shown.getFirst().service())
                    .with(FollowUpCalls.DIRECTION, side)
                    .why(SERVICE_WHY));
        }
        McpFollowUp followUp = steps
                .next(call(FollowUpCalls.GRPC_TRAFFIC).with(FollowUpCalls.DIRECTION, side).why(TRAFFIC_WHY))
                .nextWhen(data.header().errorCount() > 0, call(FollowUpCalls.TRACES_NOTIFICATIONS).why(ERRORS_WHY))
                .nextWhen(!notRecorded(other(side)), otherSide(side))
                .followUp();
        return McpToolResult.of(new GrpcDashboard(DashboardStatus.OK, null, profileId(), side,
                GrpcTotals.of(data.header()),
                shown.stream().map(GrpcServiceRow::of).toList(),
                data.services().size() - shown.size(),
                data.statusCodes(),
                data.slowCalls().stream().map(GrpcCall::of).toList(),
                followUp,
                uiLink));
    }

    @Tool(description = "Returns one gRPC service in detail, broken down by method: percentiles per "
            + "method in nanoseconds, the status codes it returned and its slowest calls - a service "
            + "grpc_overview ranked. An unknown service is an error naming it. status NOT_RECORDED: "
            + "the recording did not capture that direction's exchange events.")
    @McpOutputSchema(GrpcServiceDetail.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult service(
            @ToolParam(required = true, description = "Service name exactly as recorded, taken from the services list "
                    + "in grpc_overview.")
            String service,
            @ToolParam(required = false, description = "Which side to report on: SERVER for calls this application "
                    + "answered (the default), CLIENT for calls it made to somebody else.")
            ExchangeDirection direction) {

        // Insisted on rather than passed through: the manager reads a null service as "no filter", so
        // an omitted one would return every service's methods under the heading of one service.
        String name = ToolArguments.required(service, "service", NO_SERVICE_RECOVERY);

        ExchangeDirection side = direction == null ? ExchangeDirection.SERVER : direction;
        String uiLink = UiLinks.view(profileId(), SERVICES_VIEW, serviceQuery(side, name));
        if (notRecorded(side)) {
            return McpToolResult.of(new GrpcServiceDetail(DashboardStatus.NOT_RECORDED, noData(side), profileId(),
                    side, name, null, List.of(), List.of(), List.of(), notRecordedFollowUp(side), uiLink));
        }

        GrpcServiceDetailData data = profileManager.custom().grpcManager(side).serviceDetailData(name);
        if (data.methods().isEmpty()) {
            throw new ToolExecutionException(NO_SUCH_SERVICE.formatted(name));
        }

        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(call(FollowUpCalls.GRPC_TRAFFIC).with(FollowUpCalls.DIRECTION, side).why(TRAFFIC_WHY))
                .nextWhen(data.header().errorCount() > 0, call(FollowUpCalls.TRACES_NOTIFICATIONS).why(ERRORS_WHY))
                .followUp();
        return McpToolResult.of(new GrpcServiceDetail(DashboardStatus.OK, null, profileId(), side, name,
                GrpcTotals.of(data.header()),
                data.methods().stream().map(GrpcMethodRow::of).toList(),
                data.statusCodes(),
                data.slowCalls().stream().map(GrpcCall::of).toList(),
                followUp,
                uiLink));
    }

    @Tool(description = "Returns gRPC message sizes rather than timings: bytes sent and received, "
            + "average and maximum request and response sizes, the size-bucket distribution and the "
            + "largest individual calls with their UTC epoch-millisecond instant. An oversized "
            + "payload shows up here before it is visible in the latency percentiles. status "
            + "NOT_RECORDED: the recording did not capture that direction's exchange events.")
    @McpOutputSchema(GrpcTraffic.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult traffic(
            @ToolParam(required = false, description = "Which side to report on: SERVER for calls this application "
                    + "answered (the default), CLIENT for calls it made to somebody else.")
            ExchangeDirection direction) {

        ExchangeDirection side = direction == null ? ExchangeDirection.SERVER : direction;
        String uiLink = UiLinks.view(profileId(), TRAFFIC_VIEW, mode(side));
        if (notRecorded(side)) {
            return McpToolResult.of(new GrpcTraffic(DashboardStatus.NOT_RECORDED, noData(side), profileId(), side,
                    null, List.of(), List.of(), notRecordedFollowUp(side), uiLink));
        }

        GrpcTrafficData data = profileManager.custom().grpcManager(side).trafficData();
        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(call(FollowUpCalls.GRPC_OVERVIEW).with(FollowUpCalls.DIRECTION, side).why(TIMINGS_WHY))
                .followUp();
        return McpToolResult.of(new GrpcTraffic(DashboardStatus.OK, null, profileId(), side,
                GrpcTotals.of(data.header()),
                data.sizeBuckets(),
                data.largestCalls().stream().map(GrpcLargeCall::of).toList(),
                followUp,
                uiLink));
    }

    /** A direction that was not recorded: the other one, when that one was. */
    private McpFollowUp notRecordedFollowUp(ExchangeDirection side) {
        return NextSteps.builder(advertised)
                .nextWhen(!notRecorded(other(side)), otherSide(side))
                .followUp();
    }

    private McpNextTool otherSide(ExchangeDirection side) {
        return call(FollowUpCalls.GRPC_OVERVIEW).with(FollowUpCalls.DIRECTION, other(side)).why(OTHER_SIDE_WHY);
    }

    private McpNextTool.Call call(String tool) {
        return NextCalls.to(tool).with(FollowUpCalls.PROFILE_ID, profileId());
    }

    private boolean notRecorded(ExchangeDirection side) {
        return DashboardFeature.missing(profileManager, feature(side));
    }

    private static ExchangeDirection other(ExchangeDirection side) {
        return side == ExchangeDirection.SERVER ? ExchangeDirection.CLIENT : ExchangeDirection.SERVER;
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

    private String profileId() {
        return profileManager.info().id();
    }

    /** The direction's totals: durations in nanoseconds, sizes in bytes. */
    record GrpcTotals(
            long callCount,
            long maxResponseTimeNanos,
            long p99ResponseTimeNanos,
            long p95ResponseTimeNanos,
            @McpDescription("Share of calls that ended OK, from 0 to 1")
            double successRate,
            long errorCount,
            long totalBytesSent,
            long totalBytesReceived,
            long avgRequestSizeBytes,
            long avgResponseSizeBytes,
            long maxRequestSizeBytes,
            long maxResponseSizeBytes) {

        static GrpcTotals of(GrpcHeader header) {
            return new GrpcTotals(header.callCount(), header.maxResponseTime(), header.p99ResponseTime(),
                    header.p95ResponseTime(), Figures.number(header.successRate()), header.errorCount(),
                    header.totalBytesSent(), header.totalBytesReceived(), header.avgRequestSize(),
                    header.avgResponseSize(), header.maxRequestSize(), header.maxResponseSize());
        }
    }

    /** One service's row in the ranking. */
    record GrpcServiceRow(
            String service,
            long callCount,
            long maxResponseTimeNanos,
            long p99ResponseTimeNanos,
            long p95ResponseTimeNanos,
            @McpDescription("Share of calls that ended OK, from 0 to 1")
            double successRate,
            long avgRequestSizeBytes,
            long avgResponseSizeBytes) {

        static GrpcServiceRow of(GrpcServiceInfo info) {
            return new GrpcServiceRow(info.service(), info.callCount(), info.maxResponseTime(),
                    info.p99ResponseTime(), info.p95ResponseTime(), Figures.number(info.successRate()),
                    info.avgRequestSize(), info.avgResponseSize());
        }
    }

    /** One method of a service. */
    record GrpcMethodRow(
            String method,
            long callCount,
            long maxResponseTimeNanos,
            long p99ResponseTimeNanos,
            long p95ResponseTimeNanos,
            @McpDescription("Share of calls that ended OK, from 0 to 1")
            double successRate,
            long avgRequestSizeBytes,
            long avgResponseSizeBytes) {

        static GrpcMethodRow of(GrpcMethodInfo info) {
            return new GrpcMethodRow(info.method(), info.callCount(), info.maxResponseTime(),
                    info.p99ResponseTime(), info.p95ResponseTime(), Figures.number(info.successRate()),
                    info.avgRequestSize(), info.avgResponseSize());
        }
    }

    /** One slow call; what the event did not carry is null. */
    record GrpcCall(
            String service,
            String method,
            long responseTimeNanos,
            String status,
            @McpNullable
            Long requestSizeBytes,
            @McpNullable
            Long responseSizeBytes,
            @McpNullable
            String host,
            @McpNullable
            Integer port,
            @McpDescription("When the call started, as UTC epoch milliseconds")
            long atEpochMs) {

        static GrpcCall of(GrpcSlowCall call) {
            return new GrpcCall(call.service(), call.method(), call.responseTime(), call.status(),
                    Figures.bytes(call.requestSize()), Figures.bytes(call.responseSize()),
                    Figures.name(call.host()), Figures.port(call.port()), call.timestamp());
        }
    }

    /** One of the largest calls; a size the event did not carry is null. */
    record GrpcLargeCall(
            String service,
            String method,
            @McpNullable
            Long requestSizeBytes,
            @McpNullable
            Long responseSizeBytes,
            @McpDescription("Request and response together, counting a size not recorded as 0")
            long totalSizeBytes,
            long responseTimeNanos,
            String status,
            @McpDescription("When the call started, as UTC epoch milliseconds")
            long atEpochMs) {

        static GrpcLargeCall of(GrpcLargestCall call) {
            return new GrpcLargeCall(call.service(), call.method(), Figures.bytes(call.requestSize()),
                    Figures.bytes(call.responseSize()), call.totalSize(), call.responseTime(), call.status(),
                    call.timestamp());
        }
    }

    record GrpcDashboard(
            DashboardStatus status,
            @McpNullable
            @McpDescription("Why there is no dashboard; null when status is OK")
            String reason,
            String profileId,
            ExchangeDirection direction,
            @McpNullable
            @McpDescription("The direction's totals; null when status is NOT_RECORDED")
            GrpcTotals header,
            @McpDescription("The busiest services, at most 40")
            List<GrpcServiceRow> services,
            @McpNullable
            @McpDescription("Services left out of services by its cap of 40; null when status is NOT_RECORDED")
            Integer omittedServices,
            List<GrpcStatusStats> statusCodes,
            List<GrpcCall> slowCalls,
            McpFollowUp followUp,
            @McpDescription("The direction's gRPC dashboard in the Microscope UI, for the user")
            String uiLink) {
    }

    record GrpcServiceDetail(
            DashboardStatus status,
            @McpNullable
            @McpDescription("Why there is no detail; null when status is OK")
            String reason,
            String profileId,
            ExchangeDirection direction,
            String service,
            @McpNullable
            @McpDescription("The service's totals; null when status is NOT_RECORDED")
            GrpcTotals header,
            List<GrpcMethodRow> methods,
            List<GrpcStatusStats> statusCodes,
            List<GrpcCall> slowCalls,
            McpFollowUp followUp,
            @McpDescription("The service in the Microscope services view, for the user")
            String uiLink) {
    }

    record GrpcTraffic(
            DashboardStatus status,
            @McpNullable
            @McpDescription("Why there is no traffic dashboard; null when status is OK")
            String reason,
            String profileId,
            ExchangeDirection direction,
            @McpNullable
            @McpDescription("The direction's totals; null when status is NOT_RECORDED")
            GrpcTotals header,
            List<GrpcSizeBucket> sizeBuckets,
            List<GrpcLargeCall> largestCalls,
            McpFollowUp followUp,
            @McpDescription("The direction's gRPC traffic page in the Microscope UI, for the user")
            String uiLink) {
    }
}
