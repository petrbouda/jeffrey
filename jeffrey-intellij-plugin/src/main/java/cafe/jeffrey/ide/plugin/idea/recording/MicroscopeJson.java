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

package cafe.jeffrey.ide.plugin.idea.recording;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the two Microscope responses the panel depends on.
 *
 * <p>Explicit field-by-field parsing, matching {@link cafe.jeffrey.ide.plugin.idea.util.Json} in the
 * other direction: independent of the bundled Gson version's record support, and — more to the point
 * — every missing field has a stated default here rather than a null that surfaces three frames
 * later as a blank row in the panel.
 */
final class MicroscopeJson {

    private MicroscopeJson() {
    }

    static RecordingState parseState(String body, String fallbackFilename, long fallbackSize) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        RecordingState.Status status = RecordingState.Status.of(string(root, "state"));

        return new RecordingState(
                status,
                string(root, "recordingId"),
                string(root, "profileId"),
                stringOr(root, "filename", fallbackFilename),
                longOr(root, "sizeInBytes", fallbackSize),
                parseSummary(object(root, "summary")));
    }

    /** Both {@code /from-path} and {@code /analyze} answer with a single id under a known name. */
    static String parseId(String body, String field) {
        return string(JsonParser.parseString(body).getAsJsonObject(), field);
    }

    private static RecordingState.ProfileSummary parseSummary(JsonObject summary) {
        if (summary == null) {
            return null;
        }
        return new RecordingState.ProfileSummary(
                string(summary, "profileName"),
                longOr(summary, "durationInMillis", 0L),
                longOr(summary, "sampleCount", 0L),
                (int) longOr(summary, "eventTypeCount", 0L),
                longOr(summary, "capturedSamples", 0L),
                longOr(summary, "lostSamples", 0L),
                booleanOr(summary, "analysisComputed", false),
                parseFindings(array(summary, "findings")),
                parseStrings(array(summary, "disabledFeatures")));
    }

    private static List<RecordingState.Finding> parseFindings(JsonArray findings) {
        List<RecordingState.Finding> parsed = new ArrayList<>();
        if (findings == null) {
            return parsed;
        }
        for (JsonElement element : findings) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject finding = element.getAsJsonObject();
            parsed.add(new RecordingState.Finding(
                    string(finding, "rule"),
                    string(finding, "severity"),
                    string(finding, "summary")));
        }
        return parsed;
    }

    private static List<String> parseStrings(JsonArray values) {
        List<String> parsed = new ArrayList<>();
        if (values == null) {
            return parsed;
        }
        for (JsonElement element : values) {
            if (element.isJsonPrimitive()) {
                parsed.add(element.getAsString());
            }
        }
        return parsed;
    }

    private static boolean present(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull();
    }

    private static String string(JsonObject object, String key) {
        return present(object, key) ? object.get(key).getAsString() : null;
    }

    private static String stringOr(JsonObject object, String key, String fallback) {
        String value = string(object, key);
        return value == null ? fallback : value;
    }

    private static long longOr(JsonObject object, String key, long fallback) {
        return present(object, key) ? object.get(key).getAsLong() : fallback;
    }

    private static boolean booleanOr(JsonObject object, String key, boolean fallback) {
        return present(object, key) ? object.get(key).getAsBoolean() : fallback;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return present(parent, key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }

    private static JsonArray array(JsonObject parent, String key) {
        return present(parent, key) && parent.get(key).isJsonArray() ? parent.getAsJsonArray(key) : null;
    }
}
