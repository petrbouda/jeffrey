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
import cafe.jeffrey.ide.plugin.idea.dto.SourceResponse;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Explicit Gson {@link JsonWriter}/{@link JsonParser} (de)serialization for the wire DTOs. Done by
 * hand rather than via reflective binding so it is independent of the bundled Gson version's record
 * support. All writers return UTF-8 bytes ready for {@code RestService.send(...)}.
 */
public final class Json {

    private Json() {
    }

    public static byte[] ping(int protocolVersion) {
        return write(w -> {
            w.beginObject();
            w.name("ok").value(true);
            w.name("protocolVersion").value(protocolVersion);
            w.endObject();
        });
    }

    public static byte[] has(boolean found, String projectId) {
        return write(w -> {
            w.beginObject();
            w.name("found").value(found);
            w.name("projectId").value(projectId);
            w.endObject();
        });
    }

    public static byte[] instance(InstanceResponse r) {
        return write(w -> {
            w.beginObject();
            w.name("protocolVersion").value(r.protocolVersion());
            w.name("instanceId").value(r.instanceId());
            w.name("ideName").value(r.ideName());
            w.name("ideEdition").value(r.ideEdition());
            w.name("ideVersion").value(r.ideVersion());
            w.name("pid").value(r.pid());
            w.name("port").value(r.port());
            w.name("startedAt").value(r.startedAt());
            w.name("projects").beginArray();
            for (ProjectInfo p : r.projects()) {
                writeProject(w, p);
            }
            w.endArray();
            w.endObject();
        });
    }

    private static void writeProject(JsonWriter w, ProjectInfo p) throws IOException {
        w.beginObject();
        w.name("id").value(p.id());
        w.name("name").value(p.name());
        w.name("basePath").value(p.basePath());
        w.name("trusted").value(p.trusted());
        w.name("focused").value(p.focused());
        w.name("vcsBranch").value(p.vcsBranch());
        w.endObject();
    }

    public static byte[] navigate(NavigateResponse r) {
        return write(w -> {
            w.beginObject();
            w.name("resolved").value(r.resolved());
            w.name("source").value(r.source());
            w.name("file").value(r.file());
            if (r.line() == null) {
                w.name("line").nullValue();
            } else {
                w.name("line").value(r.line().intValue());
            }
            w.name("decompiled").value(r.decompiled());
            w.name("imprecise").value(r.imprecise());
            w.name("stale").value(r.stale());
            w.name("sourceMTime").value(r.sourceMTime());
            w.name("reason").value(r.reason());
            w.endObject();
        });
    }

    public static byte[] source(SourceResponse r) {
        return write(w -> {
            w.beginObject();
            w.name("resolved").value(r.resolved());
            w.name("content").value(r.content());
            w.name("file").value(r.file());
            w.name("decompiled").value(r.decompiled());
            w.name("reason").value(r.reason());
            w.endObject();
        });
    }

    /** Parses a {@code NavigateRequest} body; missing fields default to null / {@code -1}. */
    public static NavigateRequest parseNavigate(String body) {
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        return new NavigateRequest(
                string(o, "projectId"),
                string(o, "className"),
                string(o, "methodName"),
                intOr(o, "lineNumber", -1),
                string(o, "recordingTime"));
    }

    private static String string(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static int intOr(JsonObject o, String key, int fallback) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : fallback;
    }

    private interface Body {
        void write(JsonWriter w) throws IOException;
    }

    private static byte[] write(Body body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Writer osw = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             JsonWriter w = new JsonWriter(osw)) {
            body.write(w);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
