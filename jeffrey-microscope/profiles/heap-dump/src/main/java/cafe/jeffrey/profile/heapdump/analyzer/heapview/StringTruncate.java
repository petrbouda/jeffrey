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
package cafe.jeffrey.profile.heapdump.analyzer.heapview;

/**
 * Truncates a preview string at a char cap, appending an ellipsis when the
 * source overflowed. Replaces a handful of analyzer-local copies that all
 * did the same {@code substring + "…"} dance with their own caps.
 */
public final class StringTruncate {

    private static final String ELLIPSIS = "…";

    private StringTruncate() {
    }

    public static String to(String s, int maxChars) {
        if (s == null) {
            return null;
        }
        return s.length() <= maxChars ? s : s.substring(0, maxChars) + ELLIPSIS;
    }
}
