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

package cafe.jeffrey.microscope.core.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiLinksTest {

    private static final String PROFILE_ID = "p-1";

    @BeforeEach
    void bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8585);
        // A tool is invoked while the MCP endpoint is being served, so the builder sees that path.
        request.setRequestURI("/api/internal/mcp");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    class Paths {

        @Test
        void profileLinkPointsAtTheProfileRoot() {
            assertEquals("http://localhost:8585/profiles/p-1", UiLinks.profile(PROFILE_ID));
        }

        @Test
        void viewReplacesTheServedApiPathRatherThanAppendingToIt() {
            assertEquals(
                    "http://localhost:8585/profiles/p-1/garbage-collection",
                    UiLinks.view(PROFILE_ID, "garbage-collection"));
        }

        @Test
        void aMultiSegmentViewKeepsItsSlashes() {
            assertEquals(
                    "http://localhost:8585/profiles/p-1/heap-dump/leak-suspects",
                    UiLinks.view(PROFILE_ID, "heap-dump/leak-suspects"));
        }
    }

    @Nested
    class Query {

        @Test
        void blankAndNullValuesAreLeftOutSoOptionalArgumentsCanBePassedStraightThrough() {
            Map<String, String> query = UiLinks.query();
            query.put("mode", "server");
            query.put("uri", null);
            query.put("service", "  ");

            String url = UiLinks.view(PROFILE_ID, "technologies/http/overview", query);

            assertEquals("http://localhost:8585/profiles/p-1/technologies/http/overview?mode=server", url);
        }

        @Test
        void valuesAreEncoded() {
            Map<String, String> query = UiLinks.query();
            query.put("uri", "/api/orders list");

            String url = UiLinks.view(PROFILE_ID, "technologies/http/endpoints", query);

            assertTrue(url.endsWith("?uri=%2Fapi%2Forders%20list"), url);
        }

        /**
         * Jeffrey's own endpoints are templated, so a brace reaches here routinely. The builder reads
         * braces as URI-template placeholders, which must not leak into the link or blow up on an
         * unbalanced one.
         */
        @Test
        void bracesInAValueAreEncodedRatherThanReadAsTemplateVariables() {
            Map<String, String> query = UiLinks.query();
            query.put("uri", "/api/internal/profiles/{profileId}/gc");

            String url = UiLinks.view(PROFILE_ID, "technologies/http/endpoints", query);

            assertFalse(url.contains("{"), url);
            assertTrue(url.contains("%7BprofileId%7D"), url);
        }

        @Test
        void anUnbalancedBraceDoesNotThrow() {
            Map<String, String> query = UiLinks.query();
            query.put("uri", "/api/{oops");

            assertDoesNotThrow(() -> UiLinks.view(PROFILE_ID, "technologies/http/endpoints", query));
        }

        @Test
        void anEventTypeKeepsItsDots() {
            Map<String, String> query = UiLinks.query();
            query.put("eventType", "jdk.ExecutionSample");

            assertTrue(UiLinks.view(PROFILE_ID, "flamegraph-view", query)
                    .contains("eventType=jdk.ExecutionSample"));
        }

        @Test
        void insertionOrderIsKeptSoTheSameCallAlwaysYieldsTheSameUrl() {
            Map<String, String> query = UiLinks.query();
            query.put("operation", "GET /orders");
            query.put("kind", "SERVER");
            query.put("eventType", "jeffrey.HttpServerExchange");

            String url = UiLinks.view(PROFILE_ID, "traces/operations", query);

            assertTrue(url.indexOf("operation=") < url.indexOf("kind="), url);
            assertTrue(url.indexOf("kind=") < url.indexOf("eventType="), url);
        }
    }

    @Nested
    class Flags {

        @Test
        void aTrueFlagBecomesTheLiteralTheViewsCompareAgainst() {
            assertEquals("true", UiLinks.flag(true));
        }

        @Test
        void falseAndUnsetBothDropTheParameterEntirely() {
            assertNull(UiLinks.flag(false));
            assertNull(UiLinks.flag(null));

            Map<String, String> query = UiLinks.query();
            query.put("useWeight", UiLinks.flag(true));
            query.put("useThreadMode", UiLinks.flag(false));

            String url = UiLinks.view(PROFILE_ID, "flamegraph-view", query);

            assertTrue(url.contains("useWeight=true"), url);
            assertFalse(url.contains("useThreadMode"), url);
        }
    }
}
