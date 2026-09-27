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

package cafe.jeffrey.microscope.mcp.protocol;

/**
 * Caps the text a tool hands a client, and says so when it had to cut.
 * <p>
 * Silent truncation is the failure mode this exists to prevent: a capped list looks exactly like a
 * complete one, and a model that cannot see the cap reports the visible part as the whole story. Every
 * text that hit the ceiling therefore ends with a line naming it. The ceiling is the server's; the
 * dispatcher applies its own ({@link McpDispatcherSettings#maxResultChars()}) to every text result.
 */
public final class McpText {

    private static final String TRUNCATION_NOTE =
            "\n\n_TRUNCATED: the result exceeded %d characters and was cut here. "
                    + "Narrow the query — a smaller limit, a time range, or a more specific filter._";

    private McpText() {
    }

    /**
     * Caps a text at {@code limit} characters, appending an explicit note when anything was dropped.
     * A null text is empty.
     */
    public static String capped(String text, int limit) {
        if (text == null) {
            return "";
        }
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + TRUNCATION_NOTE.formatted(limit);
    }

    /**
     * Caps a text so that it, with the note announcing the cut, fits within {@code limit}: for a text
     * that something is appended to afterwards, such as a footer, which then still fits under the
     * server's ceiling when the dispatcher caps the whole text again.
     *
     * @throws IllegalArgumentException when the limit cannot hold even the note
     */
    public static String cappedWithin(String text, int limit) {
        if (text == null) {
            return "";
        }
        if (text.length() <= limit) {
            return text;
        }
        String note = TRUNCATION_NOTE.formatted(limit);
        if (note.length() > limit) {
            throw new IllegalArgumentException("A limit this small cannot hold the truncation note: limit=" + limit);
        }
        return text.substring(0, limit - note.length()) + note;
    }
}
