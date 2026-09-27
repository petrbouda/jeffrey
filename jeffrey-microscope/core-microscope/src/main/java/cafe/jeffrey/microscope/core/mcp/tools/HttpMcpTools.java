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
import cafe.jeffrey.profile.manager.custom.model.http.HttpHeader;
import cafe.jeffrey.profile.manager.custom.model.http.HttpMethodStats;
import cafe.jeffrey.profile.manager.custom.model.http.HttpOverviewData;
import cafe.jeffrey.profile.manager.custom.model.http.HttpSlowRequest;
import cafe.jeffrey.profile.manager.custom.model.http.HttpStatusStats;
import cafe.jeffrey.profile.manager.custom.model.http.HttpUriInfo;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The HTTP traffic that crossed this JVM: how much of it there was, how slow it was, which endpoints
 * carried it and which individual requests were the worst.
 * <p>
 * Both directions are answerable and they are different questions. SERVER is what this application
 * was asked to do; CLIENT is what it asked of somebody else, where a slow figure belongs to a
 * dependency and the only local fixes are to call less often or to stop waiting. Averaging the two
 * together would hide both.
 * <p>
 * The per-second response-time and request-count series that back the dashboard's charts are left out
 * of every answer: they are thousands of points describing a shape, which costs a large part of the
 * output budget and says nothing the percentiles in the header do not. The link is how the reader
 * sees the shape.
 */
public class HttpMcpTools {

    private static final MicroscopeView OVERVIEW_VIEW = MicroscopeView.HTTP_OVERVIEW;
    private static final MicroscopeView ENDPOINTS_VIEW = MicroscopeView.HTTP_ENDPOINTS;
    private static final String MODE_PARAM = "mode";
    private static final String URI_PARAM = "uri";

    /**
     * Endpoint counts are unbounded - a service that puts identifiers in its paths can produce one
     * "endpoint" per request - so the list is trimmed, the busiest kept. The slow-request list is
     * already capped by the manager itself.
     */
    private static final int MAX_ENDPOINTS = 40;

    private static final String NO_HTTP_DATA =
            "This profile holds no %s-side HTTP data: the recording did not capture %s events. That is "
                    + "a profiler-configuration finding worth reporting - the application may well "
                    + "handle HTTP in that direction, but this recording cannot show it.";

    private static final String ENDPOINT_WHY = "one endpoint's own percentiles, status codes and slowest requests";
    private static final String OTHER_SIDE_WHY =
            "the other direction of the traffic - what this application called out to, or what it served";
    private static final String FRAMES_WHY = "which frames burned the time inside the requests";
    private static final String NOTIFICATIONS_WHY = "what the application said about the requests that failed";
    private static final String FAILED_OPERATIONS_WHY = "the failed requests one by one, when this profile carries traces";
    private static final String ENDPOINT_TRACES_WHY = "this endpoint request by request, when this profile carries traces";

    private static final String NO_SUCH_ENDPOINT =
            "No requests were recorded for '%s'. The URI has to match exactly what the server saw - "
                    + "call http_overview and take one from its endpoints list.";

    private static final String NO_URI_RECOVERY =
            "Call http_overview and take a URI from its endpoints list.";

    private final ProfileManager profileManager;
    private final AdvertisedFamilies advertised;

    public HttpMcpTools(ProfileManager profileManager, AdvertisedFamilies advertised) {
        this.profileManager = profileManager;
        this.advertised = advertised;
    }

    @Tool(description = "Returns the HTTP server dashboard: total requests, response-time percentiles "
            + "in nanoseconds, success rate and 4xx/5xx counts, plus the endpoints ranked by traffic "
            + "(the busiest 40, with omittedEndpoints counting the rest), the status-code and method "
            + "breakdowns, and the slowest individual requests with their UTC epoch-millisecond "
            + "instant. Answers 'is the service slow' and 'which endpoint is the problem'. status "
            + "NOT_RECORDED: the recording did not capture that direction's exchange events.")
    @McpOutputSchema(HttpDashboard.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult overview(
            @ToolParam(required = false, description = "Which side to report on: SERVER for requests this application "
                    + "answered (the default), CLIENT for requests it made to somebody else.")
            ExchangeDirection direction) {

        ExchangeDirection side = direction == null ? ExchangeDirection.SERVER : direction;
        String uiLink = UiLinks.view(profileId(), OVERVIEW_VIEW, mode(side));
        if (notRecorded(side)) {
            return McpToolResult.of(new HttpDashboard(DashboardStatus.NOT_RECORDED, noData(side), profileId(), side,
                    null, List.of(), null, List.of(), List.of(), List.of(), notRecordedFollowUp(side), uiLink));
        }

        HttpOverviewData data = profileManager.custom().httpManager(side).overviewData();
        List<HttpUriInfo> shown = ToolArguments.firstOf(data.uris(), MAX_ENDPOINTS);
        NextSteps.Builder steps = NextSteps.builder(advertised);
        if (!shown.isEmpty()) {
            steps.next(call(FollowUpCalls.HTTP_ENDPOINT)
                    .with(FollowUpCalls.URI, shown.getFirst().uri())
                    .with(FollowUpCalls.DIRECTION, side)
                    .why(ENDPOINT_WHY));
        }
        McpFollowUp followUp = withFrames(steps
                .nextWhen(failuresOccurred(data.header()), notifications())
                .nextWhen(failuresOccurred(data.header()), failedOperations())
                .nextWhen(!notRecorded(other(side)), otherSide(side)))
                .followUp();
        return McpToolResult.of(new HttpDashboard(DashboardStatus.OK, null, profileId(), side,
                HttpTotals.of(data.header()),
                shown.stream().map(HttpEndpoint::of).toList(),
                data.uris().size() - shown.size(),
                data.statusCodes(),
                data.methods(),
                data.slowRequests().stream().map(HttpRequest::of).toList(),
                followUp,
                uiLink));
    }

    @Tool(description = "Returns one endpoint in detail: the same percentiles, status codes, methods "
            + "and slowest requests as http_overview, narrowed to a single URI. An unknown URI is an "
            + "error naming it. status NOT_RECORDED: the recording did not capture that direction's "
            + "exchange events.")
    @McpOutputSchema(HttpEndpointDetail.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult endpoint(
            @ToolParam(required = true, description = "The URI exactly as the server recorded it, e.g. '/api/orders'. "
                    + "Take it from the endpoints list in http_overview.")
            String uri,
            @ToolParam(required = false, description = "Which side the endpoint belongs to: SERVER "
                    + "(the default) or CLIENT - the one http_overview listed it under.")
            ExchangeDirection direction) {

        // Insisted on rather than passed through: the manager reads a null uri as "no filter", so an
        // omitted one would produce the whole dashboard and this tool would hand back its busiest
        // endpoint as though it were the one that was asked for.
        String endpoint = ToolArguments.required(uri, "uri", NO_URI_RECOVERY);

        ExchangeDirection side = direction == null ? ExchangeDirection.SERVER : direction;
        String uiLink = UiLinks.view(profileId(), ENDPOINTS_VIEW, endpointQuery(side, endpoint));
        if (notRecorded(side)) {
            return McpToolResult.of(new HttpEndpointDetail(DashboardStatus.NOT_RECORDED, noData(side), profileId(),
                    side, endpoint, null, null, List.of(), List.of(), List.of(), notRecordedFollowUp(side), uiLink));
        }

        HttpOverviewData data = profileManager.custom().httpManager(side).overviewData(endpoint);
        // The manager filters by URI while streaming, so an unmatched one yields an empty list rather
        // than an error. Reported as a bad argument, with real URIs to correct it from.
        if (data.uris().isEmpty()) {
            throw new ToolExecutionException(NO_SUCH_ENDPOINT.formatted(endpoint));
        }

        McpFollowUp followUp = withFrames(NextSteps.builder(advertised)
                .next(call(FollowUpCalls.TRACES_OPERATIONS).with(FollowUpCalls.SEARCH, endpoint).why(ENDPOINT_TRACES_WHY))
                .nextWhen(failuresOccurred(data.header()), notifications()))
                .followUp();
        return McpToolResult.of(new HttpEndpointDetail(DashboardStatus.OK, null, profileId(), side, endpoint,
                HttpTotals.of(data.header()),
                HttpEndpoint.of(data.uris().getFirst()),
                data.statusCodes(),
                data.methods(),
                data.slowRequests().stream().map(HttpRequest::of).toList(),
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
        return call(FollowUpCalls.HTTP_OVERVIEW).with(FollowUpCalls.DIRECTION, other(side)).why(OTHER_SIDE_WHY);
    }

    /**
     * The frames behind the requests, graphed on the on-CPU sample type this profile recorded; when it
     * recorded none, a line saying where the recorded types are instead of a call that draws nothing.
     */
    private NextSteps.Builder withFrames(NextSteps.Builder steps) {
        Optional<String> onCpu = FollowUpCalls.recordedOnCpuEvent(profileManager);
        return steps
                .nextWhen(onCpu.isPresent(), call(FollowUpCalls.FLAMEGRAPH_EXPORT)
                        .with(FollowUpCalls.EVENT_TYPE, onCpu.orElse(null))
                        .why(FRAMES_WHY))
                .guidanceWhen(onCpu.isEmpty(),
                        advertised.hint(AdvertisedFamilies.FLAMEGRAPH, FollowUpCalls.NO_ON_CPU_EVENT));
    }

    private McpNextTool notifications() {
        return call(FollowUpCalls.TRACES_NOTIFICATIONS).why(NOTIFICATIONS_WHY);
    }

    private McpNextTool failedOperations() {
        return call(FollowUpCalls.TRACES_OPERATIONS).with(FollowUpCalls.ERRORS_ONLY, true).why(FAILED_OPERATIONS_WHY);
    }

    private McpNextTool.Call call(String tool) {
        return McpNextTool.call(tool).with(FollowUpCalls.PROFILE_ID, profileId());
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

    private static Map<String, String> endpointQuery(ExchangeDirection direction, String uri) {
        Map<String, String> query = mode(direction);
        query.put(URI_PARAM, uri);
        return query;
    }

    /**
     * Which dashboard feature carries this direction. The two are recorded independently, so a profile
     * can hold one and not the other.
     */
    private static FeatureType feature(ExchangeDirection direction) {
        return direction == ExchangeDirection.SERVER
                ? FeatureType.HTTP_SERVER_DASHBOARD
                : FeatureType.HTTP_CLIENT_DASHBOARD;
    }

    private static String noData(ExchangeDirection direction) {
        return NO_HTTP_DATA.formatted(
                direction.name().toLowerCase(Locale.ROOT), direction.httpEventType().code());
    }

    /**
     * Whether any request failed at all - not whether the failure rate is high, which would be a
     * verdict rather than a route.
     */
    private static boolean failuresOccurred(HttpHeader header) {
        return header.count4xx() > 0 || header.count5xx() > 0;
    }

    private String profileId() {
        return profileManager.info().id();
    }

    /** The whole direction's figures, with every duration in nanoseconds. */
    record HttpTotals(
            long requestCount,
            long maxResponseTimeNanos,
            long p99ResponseTimeNanos,
            long p95ResponseTimeNanos,
            @McpDescription("Share of requests answered without a 4xx or 5xx, from 0 to 1")
            double successRate,
            long count5xx,
            long count4xx,
            @McpNullable
            @McpDescription("Bytes in both directions; null when no request carried a size")
            Long totalBytesTransferred,
            @McpNullable
            Long totalBytesReceived,
            @McpNullable
            Long totalBytesSent) {

        static HttpTotals of(HttpHeader header) {
            return new HttpTotals(header.requestCount(), header.maxResponseTime(), header.p99ResponseTime(),
                    header.p95ResponseTime(), Figures.number(header.successRate()), header.count5xx(),
                    header.count4xx(), Figures.bytes(header.totalBytesTransferred()),
                    Figures.bytes(header.totalBytesReceived()), Figures.bytes(header.totalBytesSent()));
        }
    }

    /** One URI's figures, with every duration in nanoseconds. */
    record HttpEndpoint(
            String uri,
            long requestCount,
            long maxResponseTimeNanos,
            long p99ResponseTimeNanos,
            long p95ResponseTimeNanos,
            @McpDescription("Share of requests answered without a 4xx or 5xx, from 0 to 1")
            double successRate,
            long count4xx,
            long count5xx,
            @McpNullable
            Long totalBytesTransferred,
            @McpNullable
            Long totalBytesReceived,
            @McpNullable
            Long totalBytesSent) {

        static HttpEndpoint of(HttpUriInfo info) {
            return new HttpEndpoint(info.uri(), info.requestCount(), info.maxResponseTime(), info.p99ResponseTime(),
                    info.p95ResponseTime(), Figures.number(info.successRate()), info.count4xx(), info.count5xx(),
                    Figures.bytes(info.totalBytesTransferred()), Figures.bytes(info.totalBytesReceived()),
                    Figures.bytes(info.totalBytesSent()));
        }
    }

    /** One slow request; what the event did not carry is null. */
    record HttpRequest(
            String uri,
            @McpNullable
            @McpDescription("The HTTP method; null when the event did not carry one")
            String method,
            long responseTimeNanos,
            int statusCode,
            @McpNullable
            Long requestSizeBytes,
            @McpNullable
            Long responseSizeBytes,
            @McpNullable
            String host,
            @McpNullable
            Integer port,
            @McpDescription("When the request started, as UTC epoch milliseconds")
            long atEpochMs) {

        static HttpRequest of(HttpSlowRequest request) {
            return new HttpRequest(request.uri(), Figures.name(request.method()), request.responseTime(),
                    request.statusCode(),
                    Figures.bytes(request.requestSize()), Figures.bytes(request.responseSize()),
                    Figures.name(request.host()), Figures.port(request.port()), request.timestamp());
        }
    }

    /**
     * The dashboard of one direction, minus its two chart series.
     *
     * @param omittedEndpoints endpoints left out of {@code endpoints} by its cap; null when not recorded
     */
    record HttpDashboard(
            DashboardStatus status,
            @McpNullable
            @McpDescription("Why there is no dashboard; null when status is OK")
            String reason,
            String profileId,
            ExchangeDirection direction,
            @McpNullable
            @McpDescription("The direction's totals; null when status is NOT_RECORDED")
            HttpTotals header,
            @McpDescription("The busiest endpoints, by request count, at most 40")
            List<HttpEndpoint> endpoints,
            @McpNullable
            @McpDescription("Endpoints left out of endpoints by its cap of 40; null when status is NOT_RECORDED")
            Integer omittedEndpoints,
            List<HttpStatusStats> statusCodes,
            List<HttpMethodStats> methods,
            List<HttpRequest> slowRequests,
            McpFollowUp followUp,
            @McpDescription("The direction's HTTP dashboard in the Microscope UI, for the user")
            String uiLink) {
    }

    /**
     * One endpoint of one direction.
     *
     * @param uri the URI asked about, as given
     */
    record HttpEndpointDetail(
            DashboardStatus status,
            @McpNullable
            @McpDescription("Why there is no detail; null when status is OK")
            String reason,
            String profileId,
            ExchangeDirection direction,
            String uri,
            @McpNullable
            @McpDescription("The endpoint's totals; null when status is NOT_RECORDED")
            HttpTotals header,
            @McpNullable
            @McpDescription("The endpoint's own row; null when status is NOT_RECORDED")
            HttpEndpoint endpoint,
            List<HttpStatusStats> statusCodes,
            List<HttpMethodStats> methods,
            List<HttpRequest> slowRequests,
            McpFollowUp followUp,
            @McpDescription("The endpoint in the Microscope endpoints view, for the user")
            String uiLink) {
    }
}
