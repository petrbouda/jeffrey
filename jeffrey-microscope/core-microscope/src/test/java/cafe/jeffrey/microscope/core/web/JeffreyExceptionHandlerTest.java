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

package cafe.jeffrey.microscope.core.web;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import static org.assertj.core.api.Assertions.assertThat;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;

class JeffreyExceptionHandlerTest {

    private static final String DISCONNECT_MESSAGE = "Servlet container error notification for disconnected client";

    /**
     * Stands in for a controller whose response the container has already given up on — the SSE
     * endpoints are the real-world case.
     */
    @RestController
    @RequestMapping("/api/internal/test")
    static class StubController {

        @GetMapping(value = "/disconnect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public String disconnect() throws AsyncRequestNotUsableException {
            throw new AsyncRequestNotUsableException(DISCONNECT_MESSAGE);
        }

        @GetMapping("/fail")
        public String fail() {
            throw new IllegalStateException("boom");
        }
    }

    @Nested
    class DisconnectedClient {

        /**
         * A browser closing an SSE stream must not surface as a server fault. Letting it reach the
         * catch-all handler produced an ERROR log plus a second failure, because an
         * {@code ErrorResponse} has no converter for {@code text/event-stream}.
         */
        @Test
        void isSwallowedInsteadOfBecomingAnInternalError() {
            MockMvcTester mvc = mockMvcTesterFor(new StubController());

            MvcTestResult result = mvc.get().uri("/api/internal/test/disconnect").exchange();

            assertThat(result.getUnresolvedException())
                    .as("The disconnect must be handled, not left to escape the dispatcher")
                    .isNull();
            assertThat(result)
                    .hasStatusOk()
                    .body().isEmpty();
        }
    }

    @Nested
    class GenericFailures {

        @Test
        void stillMapToAnInternalErrorResponse() {
            MockMvcTester mvc = mockMvcTesterFor(new StubController());

            assertThat(mvc.get().uri("/api/internal/test/fail"))
                    .hasStatus(500)
                    .bodyJson()
                    .extractingPath("$.message").asString().isEqualTo("boom");
        }
    }
}
