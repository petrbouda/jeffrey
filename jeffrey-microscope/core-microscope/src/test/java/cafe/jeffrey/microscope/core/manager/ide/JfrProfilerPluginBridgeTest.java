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

package cafe.jeffrey.microscope.core.manager.ide;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JfrProfilerPluginBridgeTest {

    private static final String BASE_URL = "http://localhost:4243";

    private record Fixture(JfrProfilerPluginBridge bridge, MockRestServiceServer server) {
    }

    private static Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new JfrProfilerPluginBridge(BASE_URL, builder), server);
    }

    @Test
    void isAlwaysEnabledOnceConfigured() {
        assertTrue(fixture().bridge().isEnabled());
    }

    @Test
    void modeIsJfrProfilerPlugin() {
        assertEquals(IdeMode.JFR_PROFILER_PLUGIN, fixture().bridge().mode());
    }

    @Test
    void targetStatusIsAutoLinkedButNotSelectable() {
        IdeTargetStatus status = fixture().bridge().targetStatus("p1");

        // Single-URL connection: always "linked", and there is no window to pick or disconnect.
        assertTrue(status.linked());
        assertFalse(status.selectable());
    }

    @Test
    void hasClassTrueWhenResolved() {
        Fixture f = fixture();
        f.server().expect(requestTo(BASE_URL + "/ide/has/com.acme.Foo"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"resolved\":true}", MediaType.APPLICATION_JSON));

        assertTrue(f.bridge().hasClass(null, "com.acme.Foo"));
        f.server().verify();
    }

    @Test
    void hasClassFalseWhenNotResolved() {
        Fixture f = fixture();
        f.server().expect(requestTo(BASE_URL + "/ide/has/com.acme.Foo"))
                .andRespond(withSuccess("{\"resolved\":false}", MediaType.APPLICATION_JSON));

        assertFalse(f.bridge().hasClass(null, "com.acme.Foo"));
        f.server().verify();
    }

    @Test
    void hasClassEncodesNestedClassSegment() {
        Fixture f = fixture();
        f.server().expect(requestTo(BASE_URL + "/ide/has/com.acme.Outer%24Inner"))
                .andRespond(withSuccess("{\"resolved\":true}", MediaType.APPLICATION_JSON));

        assertTrue(f.bridge().hasClass(null, "com.acme.Outer$Inner"));
        f.server().verify();
    }

    @Test
    void hasClassFalseOnServerError() {
        Fixture f = fixture();
        f.server().expect(requestTo(BASE_URL + "/ide/has/com.acme.Foo"))
                .andRespond(withServerError());

        assertFalse(f.bridge().hasClass(null, "com.acme.Foo"));
        f.server().verify();
    }

    @Test
    void hasClassFalseForBlankFqn() {
        assertFalse(fixture().bridge().hasClass(null, "  "));
    }
}
