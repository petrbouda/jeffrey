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

package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/** A live keyset position, bound to normalized filters and the original relative-time cutoff. */
public record HubSessionCursor(String filters, Instant activeFrom, HubSessionScan.Key after) {

    private static final int VERSION = 1;
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String INVALID_CURSOR =
            "Invalid hubs_sessions cursor. Copy nextCursor unchanged, or omit cursor to start again.";
    private static final String MISMATCHED_CURSOR =
            "hubs_sessions cursor does not match these filters. Keep the original filters or omit cursor.";

    public static String fingerprint(HubScanFilter filter, Integer withinLastMinutes) {
        String query = Json.toString(Arrays.asList(
                filter.hub(), filter.workspace(), filter.project(), withinLastMinutes, filter.sessions().status()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(HASH_ALGORITHM)
                    .digest(query.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Required cursor hash is unavailable", e);
        }
    }

    public String encode() {
        ObjectNode value = Json.createObject();
        value.put("version", VERSION);
        value.put("filters", filters);
        putInstant(value, "activeFrom", activeFrom);
        putInstant(value, "createdAt", after.createdAt());
        value.put("sessionRef", after.ref().encode());
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Json.toString(value).getBytes(StandardCharsets.UTF_8));
    }

    public static HubSessionCursor decode(String cursor, String expectedFilters, boolean windowed) {
        HubSessionCursor decoded;
        try {
            if (cursor == null || cursor.isBlank() || cursor.length() > McpToolOutput.MAX_CHARS) {
                throw new IllegalArgumentException(INVALID_CURSOR);
            }
            JsonNode value = Json.readTree(new String(
                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
            if (!value.isObject() || value.size() != 5 || !value.path("version").isInt()
                    || value.path("version").asInt() != VERSION
                    || !value.path("filters").isString() || !value.path("sessionRef").isString()) {
                throw new IllegalArgumentException(INVALID_CURSOR);
            }
            Instant activeFrom = instant(value.get("activeFrom"));
            if (windowed != (activeFrom != null)) {
                throw new IllegalArgumentException(INVALID_CURSOR);
            }
            decoded = new HubSessionCursor(value.path("filters").asText(), activeFrom,
                    new HubSessionScan.Key(instant(value.get("createdAt")),
                            HubSessionRef.decode(value.path("sessionRef").asText())));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(INVALID_CURSOR, e);
        }
        if (!expectedFilters.equals(decoded.filters())) {
            throw new IllegalArgumentException(MISMATCHED_CURSOR);
        }
        return decoded;
    }

    private static Instant instant(JsonNode node) {
        if (node == null || (!node.isNull() && !node.isString())) {
            throw new IllegalArgumentException(INVALID_CURSOR);
        }
        return node.isNull() ? null : Instant.parse(node.asText());
    }

    private static void putInstant(ObjectNode node, String field, Instant value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value.toString());
        }
    }
}
