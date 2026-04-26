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

package pbouda.jeffrey.server.core.web.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static pbouda.jeffrey.server.core.web.MockMvcSupport.mockMvcTesterFor;

class GrpcDocsControllerTest {

    @Test
    void servesGrpcDocsAsJson() {
        // grpc-api-docs.json is generated at build time by shared/server-api's exec
        // plugin and ships in its target/classes, so it's reliably on the test
        // classpath via the Maven dependency.
        MockMvcTester mvc = mockMvcTesterFor(new GrpcDocsController());

        assertThat(mvc.get().uri("/api/internal/grpc-docs"))
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyText().isNotEmpty().startsWith("{");
    }
}
