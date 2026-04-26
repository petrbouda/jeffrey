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

package pbouda.jeffrey.server.core.web;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import pbouda.jeffrey.shared.common.Json;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;

/**
 * Builds a {@link MockMvcTester} (Spring 6.2+ AssertJ-based MockMvc API)
 * wired the same way the server's Spring MVC dispatcher is wired in
 * production: shared Jackson 3 message converter and
 * {@link JeffreyExceptionResolver}. Mirrors {@code MockMvcSupport} in
 * core-local; kept separate because the two apps have different exception
 * resolvers (server only handles generic exceptions).
 */
public final class MockMvcSupport {

    private MockMvcSupport() {
    }

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
