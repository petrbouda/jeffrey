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

import cafe.jeffrey.microscope.core.manager.ide.JeffreyPluginClient.NavigateBody;
import cafe.jeffrey.microscope.core.manager.ide.JeffreyPluginClient.PluginInstance;
import cafe.jeffrey.microscope.core.manager.ide.JeffreyPluginClient.PluginNavigateResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JeffreyPluginClientTest {

    private static final int PORT = 63342;
    private static final String INSTANCE_JSON = """
            {"protocolVersion":1,"instanceId":"inst-1","ideName":"IntelliJ IDEA","ideEdition":"IU",
             "ideVersion":"2026.1.2","pid":4821,"port":63342,"startedAt":"2026-05-24T10:00:00Z",
             "projects":[{"id":"loc-hash","name":"order-service","basePath":"/code/order-service",
             "trusted":true,"focused":true,"vcsBranch":"main"}]}""";

    private record Fixture(JeffreyPluginClient client, MockRestServiceServer server) {
    }

    private static Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new JeffreyPluginClient(builder), server);
    }

    @Test
    void instanceParsesResponse() {
        Fixture f = fixture();
        f.server().expect(requestTo("http://127.0.0.1:63342/api/jeffrey/instance"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(INSTANCE_JSON, MediaType.APPLICATION_JSON));

        Optional<PluginInstance> result = f.client().instance(PORT);

        assertTrue(result.isPresent());
        assertEquals("inst-1", result.get().instanceId());
        assertEquals(63342, result.get().port());
        assertEquals(1, result.get().projects().size());
        assertEquals("loc-hash", result.get().projects().get(0).id());
        f.server().verify();
    }

    @Test
    void instanceReturnsEmptyOnError() {
        Fixture f = fixture();
        f.server().expect(requestTo("http://127.0.0.1:63342/api/jeffrey/instance"))
                .andRespond(withServerError());

        assertTrue(f.client().instance(PORT).isEmpty());
        f.server().verify();
    }

    @Test
    void hasReadsClassFlag() {
        Fixture f = fixture();
        f.server().expect(requestTo("http://127.0.0.1:63342/api/jeffrey/has?class=com.acme.Foo&projectId=loc-hash"))
                .andRespond(withSuccess("{\"found\":true,\"projectId\":\"loc-hash\"}", MediaType.APPLICATION_JSON));

        assertTrue(f.client().has(PORT, "loc-hash", "com.acme.Foo", null));
        f.server().verify();
    }

    @Test
    void hasReadsClassAndMethodFlag() {
        Fixture f = fixture();
        f.server().expect(requestTo("http://127.0.0.1:63342/api/jeffrey/has?class=com.acme.Foo&method=bar&projectId=loc-hash"))
                .andRespond(withSuccess("{\"found\":true,\"projectId\":\"loc-hash\"}", MediaType.APPLICATION_JSON));

        assertTrue(f.client().has(PORT, "loc-hash", "com.acme.Foo", "bar"));
        f.server().verify();
    }

    @Test
    void navigatePostsBodyAndParsesResult() {
        Fixture f = fixture();
        f.server().expect(requestTo("http://127.0.0.1:63342/api/jeffrey/navigate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"resolved\":true,\"source\":\"JAVA_LINE\",\"file\":\"/x/Foo.java\",\"line\":42,"
                                + "\"decompiled\":false,\"imprecise\":false,\"stale\":false,\"sourceMTime\":null,\"reason\":null}",
                        MediaType.APPLICATION_JSON));

        PluginNavigateResult result = f.client().navigate(PORT,
                new NavigateBody("loc-hash", "com.acme.Foo", "bar", 42, null));

        assertTrue(result.resolved());
        assertEquals("JAVA_LINE", result.source());
        assertEquals(42, result.line().intValue());
        assertFalse(result.decompiled());
        f.server().verify();
    }
}
