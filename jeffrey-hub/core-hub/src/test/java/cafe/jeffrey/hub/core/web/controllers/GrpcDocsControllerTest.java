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

package cafe.jeffrey.hub.core.web.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static cafe.jeffrey.hub.core.web.MockMvcSupport.mockMvcTesterFor;

class GrpcDocsControllerTest {

    @Test
    void servesGrpcDocsAsJson() {
        // grpc-api-docs.json is generated at build time by shared/hub-api's exec
        // plugin and ships in its target/classes, so it's reliably on the test
        // classpath via the Maven dependency.
        MockMvcTester mvc = mockMvcTesterFor(new GrpcDocsController());

        assertThat(mvc.get().uri("/api/internal/grpc-docs"))
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyText().isNotEmpty().startsWith("{");
    }
}
