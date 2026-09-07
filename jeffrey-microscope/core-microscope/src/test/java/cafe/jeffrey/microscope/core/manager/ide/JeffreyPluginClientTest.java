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
import cafe.jeffrey.microscope.core.manager.ide.JeffreyPluginClient.PluginSourceResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JeffreyPluginClientTest {

    private static final int PORT = 63342;
    /**
     * What the current plugin actually answers. It served protocol 1 without a head commit when this
     * fixture was written, and pinning that meant the two halves of the contract could drift apart
     * without a test noticing.
     */
    private static final int PLUGIN_PROTOCOL_VERSION = 2;
    private static final String INSTANCE_JSON = """
            {"protocolVersion":2,"instanceId":"inst-1","ideName":"IntelliJ IDEA","ideEdition":"IU",
             "ideVersion":"2026.1.2","pid":4821,"port":63342,"startedAt":"2026-05-24T10:00:00Z",
             "projects":[{"id":"loc-hash","name":"order-service","basePath":"/code/order-service",
             "trusted":true,"focused":true,"vcsBranch":"main","headCommit":"9f1c2ab"}]}""";

    /**
     * @param server     the discovery transport, which serves the port scan
     * @param operations the operations transport, which serves navigate, resolve and source
     */
    private static final String RESOLVE_URL = "http://127.0.0.1:63342/api/jeffrey/resolve";
    private static final String NAVIGATE_RESULT_JSON =
            "{\"resolved\":true,\"source\":\"JAVA_LINE\",\"file\":\"/x/Foo.java\",\"line\":42,"
                    + "\"decompiled\":false,\"imprecise\":false,\"stale\":false,"
                    + "\"sourceMTime\":null,\"reason\":null}";

    private record Fixture(
            JeffreyPluginClient client,
            MockRestServiceServer server,
            MockRestServiceServer operations) {
    }

    /**
     * Two transports, bound separately, because the client keeps two: a call routed to the wrong one
     * would inherit the wrong timeout, and the only way to see that from a test is to let the two
     * fail differently.
     */
    private static Fixture fixture() {
        RestClient.Builder discoveryBuilder = RestClient.builder();
        RestClient.Builder operationsBuilder = RestClient.builder();
        MockRestServiceServer discovery = MockRestServiceServer.bindTo(discoveryBuilder).build();
        MockRestServiceServer operations = MockRestServiceServer.bindTo(operationsBuilder).build();
        return new Fixture(
                new JeffreyPluginClient(discoveryBuilder, operationsBuilder), discovery, operations);
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
        assertEquals(PLUGIN_PROTOCOL_VERSION, result.get().protocolVersion());
        assertEquals("9f1c2ab", result.get().projects().get(0).headCommit());
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
        f.operations().expect(requestTo("http://127.0.0.1:63342/api/jeffrey/navigate"))
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
        f.operations().verify();
    }

    @Test
    void resolvePostsBodyAndParsesResult() {
        Fixture f = fixture();
        f.operations().expect(requestTo(RESOLVE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(NAVIGATE_RESULT_JSON, MediaType.APPLICATION_JSON));

        PluginNavigateResult result = f.client().resolve(PORT, body());

        assertTrue(result.resolved());
        assertEquals("/x/Foo.java", result.file());
        f.operations().verify();
    }

    /**
     * The only two answers that mean the plugin does not have the endpoint. It was added in protocol
     * version 2, so a plugin older than that has no route for it.
     */
    @Test
    void reportsAnAbsentResolveEndpointAsAnOldPlugin() {
        for (HttpStatus status : List.of(HttpStatus.NOT_FOUND, HttpStatus.METHOD_NOT_ALLOWED)) {
            Fixture f = fixture();
            f.operations().expect(requestTo(RESOLVE_URL)).andRespond(withStatus(status));

            assertThrows(JeffreyPluginClient.Unsupported.class, () -> f.client().resolve(PORT, body()));
            f.operations().verify();
        }
    }

    /**
     * Everything else the plugin can answer with is not an old plugin, and used to be reported as one.
     * A developer who had switched the integration off in settings, or whose lookup threw inside a
     * still-indexing IDE, was told to update a plugin that was already current.
     */
    @Test
    void doesNotCallAWorkingPluginOldWhenARequestSimplyFailed() {
        Fixture f = fixture();
        f.operations().expect(requestTo(RESOLVE_URL)).andRespond(withServerError());

        assertNull(f.client().resolve(PORT, body()));
        f.operations().verify();
    }

    @Test
    void sourceParsesResponse() {
        Fixture f = fixture();
        f.operations().expect(requestTo(
                        "http://127.0.0.1:63342/api/jeffrey/source?projectId=loc-hash&className=com.acme.Foo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"resolved\":true,\"content\":\"class Foo {}\",\"file\":\"/x/Foo.java\","
                                + "\"decompiled\":false,\"reason\":null}",
                        MediaType.APPLICATION_JSON));

        PluginSourceResult result = f.client().source(PORT, "loc-hash", "com.acme.Foo");

        assertTrue(result.resolved());
        assertEquals("class Foo {}", result.content());
        f.operations().verify();
    }

    @Test
    void sourceReturnsNullWhenTheWindowFails() {
        Fixture f = fixture();
        f.operations().expect(requestTo(
                        "http://127.0.0.1:63342/api/jeffrey/source?projectId=loc-hash&className=com.acme.Foo"))
                .andRespond(withServerError());

        assertNull(f.client().source(PORT, "loc-hash", "com.acme.Foo"));
        f.operations().verify();
    }

    private static NavigateBody body() {
        return new NavigateBody("loc-hash", "com.acme.Foo", "bar", 42, null);
    }
}
