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
import cafe.jeffrey.profile.manager.custom.model.http.HttpHeader;
import cafe.jeffrey.profile.manager.custom.model.http.HttpMethodStats;
import cafe.jeffrey.profile.manager.custom.model.http.HttpOverviewData;
import cafe.jeffrey.profile.manager.custom.model.http.HttpSlowRequest;
import cafe.jeffrey.profile.manager.custom.model.http.HttpStatusStats;
import cafe.jeffrey.profile.manager.custom.model.http.HttpUriInfo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Map;

/**
 * The inbound HTTP traffic the profiled JVM served: how much of it there was, how slow it was, which
 * endpoints carried it and which individual requests were the worst.
 * <p>
 * Only server-side exchanges exist here. {@code HttpManagerImpl} reads
 * {@code Type.HTTP_SERVER_EXCHANGE} and nothing else, so there is no client half to select and these
 * tools take no direction argument, even though the UI shows a client/server switch.
 * <p>
 * The per-second response-time and request-count series that back the dashboard's charts are left out
 * of every answer: they are thousands of points describing a shape, which costs a large part of the
 * output budget and says nothing the percentiles in the header do not. The link is how the reader
 * sees the shape.
 */
public class HttpMcpTools {

    private static final String OVERVIEW_VIEW = "technologies/http/overview";
    private static final String ENDPOINTS_VIEW = "technologies/http/endpoints";
    private static final String MODE_PARAM = "mode";
    private static final String SERVER_MODE = "server";
    private static final String URI_PARAM = "uri";

    /**
     * Endpoint counts are unbounded - a service that puts identifiers in its paths can produce one
     * "endpoint" per request - so the list is trimmed. The slow-request list is already capped by the
     * manager itself.
     */
    private static final int MAX_ENDPOINTS = 40;

    private static final String NO_HTTP_DATA =
            "This profile holds no HTTP server data: the recording did not capture "
                    + "jdk.HttpServerExchange events. That is a profiler-configuration finding worth "
                    + "reporting - the application may well serve HTTP, but this recording cannot show it.";

    private static final String STEP_ENDPOINT =
            "This is the whole service. For one URI - its own percentiles, status codes and slowest "
                    + "requests - http_endpoint takes a uri from the endpoints list above.";
    private static final String STEP_FRAMES =
            "Which frames burned the time inside a request is a flamegraph question, not an HTTP one: "
                    + "flamegraph_export with jdk.ExecutionSample.";
    private static final String STEP_FAILURES =
            "Some requests returned 4xx or 5xx. What the application said about them is in "
                    + "traces_notifications, and the individual requests are in traces_operations - "
                    + "when this profile carries traces, which profiles_features reports.";
    private static final String STEP_ONE_ENDPOINT_TRACES =
            "For this endpoint request by request rather than in aggregate, traces_operations takes "
                    + "the operation name.";

    private static final String NO_SUCH_ENDPOINT =
            "No requests were recorded for '%s'. The URI has to match exactly what the server saw - "
                    + "call http_overview and take one from its endpoints list.";

    private final ProfileManager profileManager;

    public HttpMcpTools(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    @Tool(description = "The HTTP server dashboard: total requests, response-time percentiles, success "
            + "rate and 4xx/5xx counts, plus the endpoints ranked by traffic, the status-code and "
            + "method breakdowns, and the slowest individual requests. Start here for 'is the service "
            + "slow' and 'which endpoint is the problem'.")
    public String overview() {
        if (DashboardFeature.missing(profileManager, FeatureType.HTTP_SERVER_DASHBOARD)) {
            return NO_HTTP_DATA;
        }

        HttpOverviewData data = profileManager.custom().httpManager().overviewData();
        return LinkedOutput.json(new HttpDashboard(
                data.header(),
                trim(data.uris()),
                data.statusCodes(),
                data.methods(),
                data.slowRequests(),
                NextSteps.builder()
                        .add(STEP_ENDPOINT)
                        .when(failuresOccurred(data.header()), STEP_FAILURES)
                        .add(STEP_FRAMES)
                        .build(),
                UiLinks.view(profileId(), OVERVIEW_VIEW, mode())));
    }

    @Tool(description = "One endpoint in detail: the same percentiles, status codes, methods and "
            + "slowest requests as the overview, but for a single URI. Use it after http_overview has "
            + "named the endpoint worth looking at.")
    public String endpoint(
            @ToolParam(description = "The URI exactly as the server recorded it, e.g. '/api/orders'. "
                    + "Take it from the endpoints list in http_overview.")
            String uri) {

        if (DashboardFeature.missing(profileManager, FeatureType.HTTP_SERVER_DASHBOARD)) {
            return NO_HTTP_DATA;
        }

        HttpOverviewData data = profileManager.custom().httpManager().overviewData(uri);
        // The manager filters by URI while streaming, so an unmatched one yields an empty list rather
        // than an error. Reported as a bad argument, with real URIs to correct it from.
        if (data.uris().isEmpty()) {
            return NO_SUCH_ENDPOINT.formatted(uri);
        }

        return LinkedOutput.json(new HttpEndpointDetail(
                data.header(),
                data.uris().getFirst(),
                data.statusCodes(),
                data.methods(),
                data.slowRequests(),
                NextSteps.builder()
                        .add(STEP_ONE_ENDPOINT_TRACES)
                        .when(failuresOccurred(data.header()), STEP_FAILURES)
                        .add(STEP_FRAMES)
                        .build(),
                UiLinks.view(profileId(), ENDPOINTS_VIEW, endpointQuery(uri))));
    }

    private Map<String, String> mode() {
        Map<String, String> query = UiLinks.query();
        query.put(MODE_PARAM, SERVER_MODE);
        return query;
    }

    private Map<String, String> endpointQuery(String uri) {
        Map<String, String> query = mode();
        query.put(URI_PARAM, uri);
        return query;
    }

    /**
     * Whether any request failed at all - not whether the failure rate is high, which would be a
     * verdict rather than a route.
     */
    private static boolean failuresOccurred(HttpHeader header) {
        return header.count4xx() > 0 || header.count5xx() > 0;
    }

    private static List<HttpUriInfo> trim(List<HttpUriInfo> uris) {
        return uris.size() <= MAX_ENDPOINTS ? uris : uris.subList(0, MAX_ENDPOINTS);
    }

    private String profileId() {
        return profileManager.info().id();
    }

    /**
     * The dashboard minus its two chart series. The nested records are the manager's own - they are
     * already flat, so only the top level needs restating.
     */
    private record HttpDashboard(
            HttpHeader header,
            List<HttpUriInfo> endpoints,
            List<HttpStatusStats> statusCodes,
            List<HttpMethodStats> methods,
            List<HttpSlowRequest> slowRequests,
            List<String> nextSteps,
            String uiLink) {
    }

    private record HttpEndpointDetail(
            HttpHeader header,
            HttpUriInfo endpoint,
            List<HttpStatusStats> statusCodes,
            List<HttpMethodStats> methods,
            List<HttpSlowRequest> slowRequests,
            List<String> nextSteps,
            String uiLink) {
    }
}
