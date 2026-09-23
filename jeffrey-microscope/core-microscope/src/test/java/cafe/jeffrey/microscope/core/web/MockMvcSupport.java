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

package cafe.jeffrey.microscope.core.web;

import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Builds a {@link MockMvcTester} (Spring 6.2+ AssertJ-based MockMvc API)
 * wired the same way Spring wires the production dispatcher: shared
 * Jackson 3 message converter (so custom serializers for {@code Type},
 * {@code RelativeTimeRange}, etc. apply at the HTTP boundary) and
 * {@link JeffreyExceptionHandler} (so error paths return the same
 * {@code ErrorResponse} JSON the real app does).
 *
 * <p>Used as a static-import partner of plain JUnit 5 + Mockito tests:
 *
 * <pre>{@code
 * import static org.assertj.core.api.Assertions.assertThat;
 * import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;
 *
 * @ExtendWith(MockitoExtension.class)
 * class WorkspacesControllerTest {
 *
 *     @Mock
 *     WorkspacesManager workspacesManager;
 *
 *     @Test
 *     void getsAllWorkspaces() {
 *         MockMvcTester mvc = mockMvcTesterFor(new WorkspacesController(workspacesManager));
 *
 *         assertThat(mvc.get().uri("/api/internal/workspaces"))
 *                 .hasStatusOk()
 *                 .bodyJson()
 *                 .extractingPath("$[0].id").asString().isEqualTo("ws-1");
 *     }
 * }
 * }</pre>
 */
public final class MockMvcSupport {

    private MockMvcSupport() {
    }

    /**
     * Builds a {@link MockMvcTester} that serves the given controllers.
     */
    public static MockMvcTester mockMvcTesterFor(Object... controllers) {
        return MockMvcTester.of(
                Arrays.asList(controllers),
                builder -> builder
                        // The String converter mirrors production wiring: without it an endpoint
                        // returning a plain String (the Markdown AI exports) has no writer here
                        // and 500s in the test while working in the real app. Declared after the
                        // JSON converter so object-returning endpoints keep negotiating JSON first.
                        .setMessageConverters(
                                new JacksonJsonHttpMessageConverter((JsonMapper) Json.mapper()),
                                new StringHttpMessageConverter(StandardCharsets.UTF_8))
                        .setControllerAdvice(new JeffreyExceptionHandler())
                        .build());
    }
}
