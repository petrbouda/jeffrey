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

    private static final String PIPELINE_RUNNING = "running";
    private static final String PIPELINE_FAILED = "failed";
    private static final String STAGE_IN_PROGRESS = "in_progress";
    private static final String STAGE_COMPLETED = "completed";
    private static final String STAGE_FAILED = "failed";

    /**
     * The heap pipeline's progress as one line, or {@code null} when there is no build to draw — the
     * pipeline is idle, or it completed, and either way the panel's next move is to ask for the
     * profile again.
     */
    static HeapIndexBuild parseIndexBuild(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        String state = string(root, "state");
        boolean running = PIPELINE_RUNNING.equals(state);
        boolean failed = PIPELINE_FAILED.equals(state);
        if (!running && !failed) {
            return null;
        }

        JsonArray stages = array(root, "stages");
        int count = stages == null ? 0 : stages.size();
        int completed = 0;
        int current = 0;
        String currentId = null;
        long elapsed = 0L;
        if (stages != null) {
            int position = 0;
            for (JsonElement element : stages) {
                position++;
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject stage = element.getAsJsonObject();
                String status = string(stage, "status");
                if (STAGE_COMPLETED.equals(status)) {
                    completed++;
                    elapsed += longOr(stage, "durationMs", 0L);
                } else if (STAGE_IN_PROGRESS.equals(status) || STAGE_FAILED.equals(status)) {
                    if (current == 0) {
                        current = position;
                        currentId = string(stage, "id");
                    }
                    elapsed += longOr(stage, "elapsedMs", longOr(stage, "durationMs", 0L));
                }
            }
        }
        // Nothing in progress yet (the run was just started) or nothing marked failed: the stage
        // after the last finished one is the one to name.
        if (current == 0) {
            current = Math.min(count, completed + 1);
            currentId = stageIdAt(stages, current);
        }

        String error = failed ? stringOr(root, "errorMessage", string(root, "errorCode")) : null;
        return new HeapIndexBuild(
                failed ? HeapIndexBuild.Phase.FAILED : HeapIndexBuild.Phase.RUNNING,
                current, count, currentId, elapsed, error);
    }

    private static String stageIdAt(JsonArray stages, int position) {
        if (stages == null || position < 1 || position > stages.size()) {
            return null;
        }
        JsonElement element = stages.get(position - 1);
        return element.isJsonObject() ? string(element.getAsJsonObject(), "id") : null;
    }

    private static RecordingState.ProfileSummary parseSummary(JsonObject summary) {
        if (summary == null) {
            return null;
        }
        return new RecordingState.ProfileSummary(
                RecordingState.Kind.of(string(summary, "kind")),
                string(summary, "profileName"),
                parseRecordingFigures(object(summary, "recording")),
                parseHeapFigures(object(summary, "heap")),
                booleanOr(summary, "analysisComputed", false),
                parseFindings(array(summary, "findings")),
                parseStrings(array(summary, "disabledFeatures")));
    }

    private static RecordingState.RecordingFigures parseRecordingFigures(JsonObject figures) {
        if (figures == null) {
            return null;
        }
        return new RecordingState.RecordingFigures(
                longOr(figures, "durationInMillis", 0L),
                longOr(figures, "sampleCount", 0L),
                (int) longOr(figures, "eventTypeCount", 0L),
                longOr(figures, "capturedSamples", 0L),
                longOr(figures, "lostSamples", 0L));
    }

    private static RecordingState.HeapFigures parseHeapFigures(JsonObject figures) {
        if (figures == null) {
            return null;
        }
        return new RecordingState.HeapFigures(
                longOr(figures, "totalBytes", 0L),
                longOr(figures, "totalInstances", 0L),
                (int) longOr(figures, "classCount", 0L),
                (int) longOr(figures, "gcRootCount", 0L),
                booleanOr(figures, "cacheReady", false));
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
