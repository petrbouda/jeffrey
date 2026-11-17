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

package cafe.jeffrey.hub.core.web;

import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;

/**
 * Builds a {@link MockMvcTester} wired the same way the server's Spring MVC
 * dispatcher is wired in production: shared Jackson 3 message converter and
 * {@link JeffreyExceptionHandler}. Mirrors the core-microscope version.
 */
public final class MockMvcSupport {

    private MockMvcSupport() {
    }

    public static MockMvcTester mockMvcTesterFor(Object... controllers) {
        return MockMvcTester.of(
                Arrays.asList(controllers),
                builder -> builder
                        .setMessageConverters(new JacksonJsonHttpMessageConverter((JsonMapper) Json.mapper()))
                        .setControllerAdvice(new JeffreyExceptionHandler())
                        .build());
    }
}
