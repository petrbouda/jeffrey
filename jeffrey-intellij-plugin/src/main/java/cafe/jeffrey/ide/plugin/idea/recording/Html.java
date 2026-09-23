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

package cafe.jeffrey.ide.plugin.idea.recording;

/**
 * Escaping for the panel's markup.
 *
 * <p>One implementation for both renderers. The Swing document and the web document share nothing
 * else — different engines, different layout, different icons — but a filename with an ampersand in
 * it has to survive both, and two copies of an escape routine is how one of them quietly stops
 * escaping something.
 */
public final class Html {

    private Html() {
    }

    /** Escapes text for a body position or a single-quoted attribute value. */
    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
