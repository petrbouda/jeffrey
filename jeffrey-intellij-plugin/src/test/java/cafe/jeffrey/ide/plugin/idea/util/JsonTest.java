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
                List.of(new ProjectInfo("loc-hash", "order-service", "/code/order-service", true, true, "main")));
        JsonObject o = parse(Json.instance(response));
        assertEquals("inst-1", o.get("instanceId").getAsString());
        assertEquals(63342, o.get("port").getAsInt());
        JsonArray projects = o.getAsJsonArray("projects");
        assertEquals(1, projects.size());
        JsonObject project = projects.get(0).getAsJsonObject();
        assertEquals("loc-hash", project.get("id").getAsString());
        assertEquals("main", project.get("vcsBranch").getAsString());
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
