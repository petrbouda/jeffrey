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

import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.ProfileCustomManager;
import cafe.jeffrey.profile.manager.ProfileFeaturesManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.custom.HttpManager;
import cafe.jeffrey.profile.manager.custom.model.http.HttpHeader;
import cafe.jeffrey.profile.manager.custom.model.http.HttpMethodStats;
import cafe.jeffrey.profile.manager.custom.model.http.HttpOverviewData;
import cafe.jeffrey.profile.manager.custom.model.http.HttpSlowRequest;
import cafe.jeffrey.profile.manager.custom.model.http.HttpStatusStats;
import cafe.jeffrey.profile.manager.custom.model.http.HttpUriInfo;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HttpMcpToolsTest {

    @Mock
    ProfileManager profileManager;

    @Mock
    ProfileCustomManager customManager;

    @Mock
    ProfileFeaturesManager featuresManager;

    @Mock
    HttpManager httpManager;

    @BeforeEach
    void setUp() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        when(profileManager.info()).thenReturn(new ProfileInfo(
                "p-1", "project-1", "workspace-1", "Profile", RecordingEventSource.JDK,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1"));
        when(profileManager.custom()).thenReturn(customManager);
        when(profileManager.featuresManager()).thenReturn(featuresManager);
        when(customManager.httpManager(any())).thenReturn(httpManager);
        when(featuresManager.getDisabledFeatures()).thenReturn(List.of());
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private HttpMcpTools tools() {
        return new HttpMcpTools(profileManager);
    }

    private static HttpOverviewData data(List<HttpUriInfo> uris) {
        return new HttpOverviewData(
                new HttpHeader(1200, 4200, 3900, 2100, new BigDecimal("99.1"), 3, 11, 900, 400, 500),
                uris,
                List.of(new HttpStatusStats(200, 1186), new HttpStatusStats(500, 3)),
                List.of(new HttpMethodStats("GET", 1200)),
                List.of(new HttpSlowRequest("/api/orders", "GET", 4200, 200, 10, 20, "host", 8080, 1)),
                null,
                null);
    }

    private static HttpUriInfo uri(String path) {
        return new HttpUriInfo(path, 600, 4200, 3900, 2100, new BigDecimal("99.1"), 5, 1, 900, 400, 500);
    }

    @Nested
    class Overview {

        @Test
        void carriesTheHeaderTotalsAndTheEndpoints() {
            when(httpManager.overviewData()).thenReturn(data(List.of(uri("/api/orders"))));

            String out = tools().overview(null);

            assertTrue(out.contains("\"requestCount\":1200"), out);
            assertTrue(out.contains("/api/orders"), out);
            assertTrue(out.contains("\"count5xx\":3"), out);
        }

        /**
         * The chart series are the bulk of the dashboard payload and say nothing the percentiles do
         * not, so they must not reach the model.
         */
        @Test
        void leavesTheChartSeriesOut() {
            when(httpManager.overviewData()).thenReturn(data(List.of(uri("/api/orders"))));

            String out = tools().overview(null);

            assertFalse(out.contains("responseTimeSerie"), out);
            assertFalse(out.contains("requestCountSerie"), out);
        }

        @Test
        void endsWithALinkToTheServerDashboard() {
            when(httpManager.overviewData()).thenReturn(data(List.of(uri("/api/orders"))));

            assertTrue(
                    tools().overview(null).contains("/profiles/p-1/technologies/http/overview?mode=server"),
                    tools().overview(null));
        }

        /**
         * An absent event type produces a well-formed zero dashboard, which reads as "the service is
         * healthy" rather than "nothing was measured".
         */
        @Test
        void reportsMissingDataAsAProfilerFindingRatherThanAnEmptyDashboard() {
            when(featuresManager.getDisabledFeatures())
                    .thenReturn(List.of(FeatureType.HTTP_SERVER_DASHBOARD));

            String out = tools().overview(null);

            assertTrue(out.contains("no server-side HTTP data"), out);
            assertFalse(out.contains("requestCount"), out);
        }
    }

    @Nested
    class Endpoint {

        @Test
        void narrowsToTheRequestedUriAndLinksToItsDetail() {
            when(httpManager.overviewData("/api/orders")).thenReturn(data(List.of(uri("/api/orders"))));

            String out = tools().endpoint("/api/orders", null);

            assertTrue(out.contains("\"endpoint\""), out);
            assertTrue(out.contains("uri=%2Fapi%2Forders"), out);
        }

        /**
         * The manager filters while streaming, so an unmatched URI yields an empty list rather than an
         * error - and the controller's equivalent would throw on getFirst().
         */
        @Test
        void reportsAnUnknownUriInsteadOfFailing() {
            when(httpManager.overviewData("/nope")).thenReturn(data(List.of()));

            String out = tools().endpoint("/nope", null);

            assertTrue(out.contains("No requests were recorded for '/nope'"), out);
        }

        /**
         * The manager reads a null uri as "no filter", so an omitted one used to produce the whole
         * dashboard and this tool handed back its busiest endpoint as though it were the one asked
         * for - an answer to a different question, with nothing in it saying so.
         */
        @Test
        void refusesAMissingUriRatherThanReportingTheBusiestEndpoint() {
            IllegalArgumentException thrown = assertThrows(
                    IllegalArgumentException.class, () -> tools().endpoint(null, null));

            assertTrue(thrown.getMessage().contains("uri is required"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("http_overview"), thrown.getMessage());
        }

        @Test
        void refusesABlankUriTheSameWay() {
            assertThrows(IllegalArgumentException.class, () -> tools().endpoint("  ", null));
        }

        @Test
        void trimsTheUriBeforeMatching() {
            when(httpManager.overviewData("/api/orders")).thenReturn(data(List.of(uri("/api/orders"))));

            assertTrue(tools().endpoint("  /api/orders  ", null).contains("\"endpoint\""));
        }
    }

    /**
     * The gate is "it happened", never "it is bad". A pointer that appeared regardless would be noise
     * on a healthy profile; one that judged the number would be a verdict the tool is not entitled to.
     */
    @Nested
    class GatedRouting {

        private static HttpOverviewData withStatuses(int count4xx, int count5xx) {
            return new HttpOverviewData(
                    new HttpHeader(1200, 4200, 3900, 2100, new BigDecimal("99.1"),
                            count5xx, count4xx, 900, 400, 500),
                    List.of(uri("/api/orders")),
                    List.of(new HttpStatusStats(200, 1186)),
                    List.of(new HttpMethodStats("GET", 1200)),
                    List.of(),
                    null,
                    null);
        }

        @Test
        void alwaysRoutesToTheEndpointDetail() {
            when(httpManager.overviewData()).thenReturn(withStatuses(0, 0));

            assertTrue(tools().overview(null).contains("http_endpoint"));
        }

        @Test
        void namesTheFailureTrailOnlyWhenSomethingActuallyFailed() {
            when(httpManager.overviewData()).thenReturn(withStatuses(0, 0));
            assertFalse(tools().overview(null).contains("traces_notifications"));

            when(httpManager.overviewData()).thenReturn(withStatuses(0, 3));
            assertTrue(tools().overview(null).contains("traces_notifications"));
        }

        @Test
        void aClientErrorCountsAsSomethingHavingHappenedToo() {
            when(httpManager.overviewData()).thenReturn(withStatuses(11, 0));

            assertTrue(tools().overview(null).contains("traces_notifications"));
        }

        /**
         * The line reports that requests failed and where the account of them lives. It must not say
         * the rate is high, or the tool has made a judgement it cannot support.
         */
        @Test
        void theGatedLineRoutesRatherThanJudging() {
            when(httpManager.overviewData()).thenReturn(withStatuses(0, 3));

            String out = tools().overview(null).toLowerCase();

            assertFalse(out.contains("too many"), out);
            assertFalse(out.contains("unacceptable"), out);
            assertFalse(out.contains("is high"), out);
        }
    }

    /**
     * The client half had a FeatureType and an event type and no manager behind it, so every client
     * question was silently answered with server figures - or, once the tools gated on the server
     * feature, refused outright.
     */
    @Nested
    class Direction {

        @Test
        void readsTheClientSideWhenAskedFor() {
            when(httpManager.overviewData()).thenReturn(data(List.of(uri("https://payments/charge"))));

            String out = tools().overview("CLIENT");

            assertTrue(out.contains("payments/charge"), out);
            assertTrue(out.contains("mode=client"), out);
        }

        @Test
        void defaultsToTheServerSide() {
            when(httpManager.overviewData()).thenReturn(data(List.of(uri("/api/orders"))));

            assertTrue(tools().overview(null).contains("mode=server"));
        }

        /**
         * The two halves are gated separately: a recording with inbound traffic and no outbound calls
         * must answer the client question with "not recorded", not with the inbound figures.
         */
        @Test
        void gatesEachDirectionOnItsOwnFeature() {
            when(featuresManager.getDisabledFeatures())
                    .thenReturn(List.of(FeatureType.HTTP_CLIENT_DASHBOARD));
            when(httpManager.overviewData()).thenReturn(data(List.of(uri("/api/orders"))));

            assertTrue(tools().overview("CLIENT").contains("no client-side HTTP data"));
            assertTrue(tools().overview("SERVER").contains("requestCount"));
        }

        @Test
        void refusesAnUnknownDirectionByName() {
            IllegalArgumentException thrown = assertThrows(
                    IllegalArgumentException.class, () -> tools().overview("inbound"));

            assertTrue(thrown.getMessage().contains("SERVER, CLIENT"), thrown.getMessage());
        }
    }
}
