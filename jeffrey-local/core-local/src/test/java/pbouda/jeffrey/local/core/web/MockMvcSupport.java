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

package pbouda.jeffrey.local.core.web;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import pbouda.jeffrey.shared.common.Json;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;

/**
 * Builds a {@link MockMvcTester} (Spring 6.2+ AssertJ-based MockMvc API)
 * wired the same way Spring wires the production dispatcher: shared
 * Jackson 3 message converter (so custom serializers for {@code Type},
 * {@code RelativeTimeRange}, etc. apply at the HTTP boundary) and
 * {@link JeffreyExceptionResolver} (so error paths return the same
 * {@code ErrorResponse} JSON the real app does).
 *
 * <p>Jeffrey's controllers are registered as explicit {@code @Bean}s with
 * {@code @RequestMapping} + {@code @ResponseBody} at class level — no
 * {@code @Controller} stereotype. The default
 * {@link StandaloneMockMvcBuilder} configures a
 * {@link RequestMappingHandlerMapping} that, in our setup, doesn't pick the
 * controller classes up. We override {@code isHandler()} so any
 * {@code @RequestMapping}-annotated class is treated as a handler — matching
 * the production behaviour.
 *
 * <p>Used as a static-import partner of plain JUnit 5 + Mockito tests:
 *
 * <pre>{@code
 * import static org.assertj.core.api.Assertions.assertThat;
 * import static pbouda.jeffrey.local.core.web.MockMvcSupport.mockMvcTesterFor;
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
                        .setCustomHandlerMapping(StandaloneRequestMappingHandlerMapping::new)
                        .setMessageConverters(new JacksonJsonHttpMessageConverter((JsonMapper) Json.mapper()))
                        .setHandlerExceptionResolvers(new JeffreyExceptionResolver())
                        .build());
    }

    private static final class StandaloneRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

        @Override
        protected boolean isHandler(Class<?> beanType) {
            return AnnotatedElementUtils.hasAnnotation(beanType, RequestMapping.class);
        }
    }
}
