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

package cafe.jeffrey.ide.plugin.idea.util;

import cafe.jeffrey.ide.plugin.idea.dto.InstanceResponse;
import cafe.jeffrey.ide.plugin.idea.dto.NavigateRequest;
import cafe.jeffrey.ide.plugin.idea.dto.NavigateResponse;
import cafe.jeffrey.ide.plugin.idea.dto.ProjectInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Pure (de)serialization tests for the wire DTOs — no IDE fixture needed. */
public class JsonTest {

    @Test
    public void parseNavigateReadsAllFields() {
        NavigateRequest req = Json.parseNavigate(
                "{\"projectId\":\"p1\",\"className\":\"com.acme.Foo\",\"methodName\":\"bar\","
                        + "\"lineNumber\":42,\"recordingTime\":\"2026-01-01T00:00:00Z\"}");
        assertEquals("p1", req.projectId());
        assertEquals("com.acme.Foo", req.className());
        assertEquals("bar", req.methodName());
        assertEquals(42, req.lineNumber());
        assertEquals("2026-01-01T00:00:00Z", req.recordingTime());
    }

    @Test
    public void parseNavigateDefaultsMissingFields() {
        NavigateRequest req = Json.parseNavigate("{\"className\":\"com.acme.Foo\"}");
        assertNull(req.projectId());
        assertNull(req.methodName());
        assertEquals(-1, req.lineNumber());
        assertNull(req.recordingTime());
    }

    @Test
    public void pingCarriesProtocolVersion() {
        JsonObject o = parse(Json.ping(7));
        assertTrue(o.get("ok").getAsBoolean());
        assertEquals(7, o.get("protocolVersion").getAsInt());
    }

    @Test
    public void instanceSerializesNestedProjects() {
        InstanceResponse response = new InstanceResponse(
                1, "inst-1", "IntelliJ IDEA", "IU", "2026.1.2", 4821, 63342, "2026-05-24T10:00:00Z",
                List.of(new ProjectInfo(
                        "loc-hash", "order-service", "/code/order-service", true, true, "main", "abc123")));
        JsonObject o = parse(Json.instance(response));
        assertEquals("inst-1", o.get("instanceId").getAsString());
        assertEquals(63342, o.get("port").getAsInt());
        JsonArray projects = o.getAsJsonArray("projects");
        assertEquals(1, projects.size());
        JsonObject project = projects.get(0).getAsJsonObject();
        assertEquals("loc-hash", project.get("id").getAsString());
        assertEquals("main", project.get("vcsBranch").getAsString());
        assertEquals("abc123", project.get("headCommit").getAsString());
        assertTrue(project.get("focused").getAsBoolean());
    }

    @Test
    public void navigateSerializesNotResolved() {
        JsonObject o = parse(Json.navigate(NavigateResponse.notResolved("class-not-found")));
        assertFalse(o.get("resolved").getAsBoolean());
        assertEquals("class-not-found", o.get("reason").getAsString());
        assertTrue(o.get("file").isJsonNull());
    }

    @Test
    public void hasSerializesFlag() {
        JsonObject o = parse(Json.has(true, "loc-hash"));
        assertTrue(o.get("found").getAsBoolean());
        assertEquals("loc-hash", o.get("projectId").getAsString());
    }

    private static JsonObject parse(byte[] bytes) {
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
