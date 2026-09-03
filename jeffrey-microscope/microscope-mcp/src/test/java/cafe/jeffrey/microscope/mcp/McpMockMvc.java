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

package cafe.jeffrey.microscope.mcp;

import cafe.jeffrey.shared.common.Json;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;

/**
 * A {@link MockMvcTester} over the given controllers with the production Jackson mapper, so the
 * JSON-RPC envelope is read and written exactly as the real dispatcher does it.
 */
final class McpMockMvc {

    private McpMockMvc() {
    }

    static MockMvcTester mockMvcTesterFor(Object... controllers) {
        return MockMvcTester.of(
                Arrays.asList(controllers),
                builder -> builder
                        .setMessageConverters(new JacksonJsonHttpMessageConverter((JsonMapper) Json.mapper()))
                        .build());
    }
}
