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


package cafe.jeffrey.microscope.core.mcp.tools;

import java.util.List;

/**
 * The application's own packages as a caller passes them ({@code framePrefixes}): trimmed, blanks and
 * repeats dropped, and bounded — every Java frame of every stack is checked against each prefix, so an
 * unbounded list would make one call cost frames times prefixes.
 */
public final class FramePrefixes {

    /** More root packages than any source tree has; a longer list is refused, not cut. */
    public static final int MAX_PREFIXES = 32;
    /** Longer than any package name; a longer prefix is refused. */
    public static final int MAX_PREFIX_CHARS = 200;

    private static final String ARGUMENT = "framePrefixes";

    private FramePrefixes() {
    }

    /**
     * The prefixes as given, trimmed, blanks and repeats dropped; empty for none.
     *
     * @throws IllegalArgumentException when more than {@link #MAX_PREFIXES} remain or one is longer than
     *                                  {@link #MAX_PREFIX_CHARS}
     */
    public static List<String> of(List<String> framePrefixes) {
        if (framePrefixes == null) {
            return List.of();
        }
        List<String> prefixes = framePrefixes.stream()
                .filter(prefix -> prefix != null && !prefix.isBlank())
                .map(String::strip)
                .distinct()
                .toList();
        if (prefixes.size() > MAX_PREFIXES) {
            throw new IllegalArgumentException(ARGUMENT + " takes at most " + MAX_PREFIXES + " packages, got "
                    + prefixes.size() + ": pass the root packages of the source tree, not every package");
        }
        for (String prefix : prefixes) {
            if (prefix.length() > MAX_PREFIX_CHARS) {
                throw new IllegalArgumentException(ARGUMENT + " entries are package names of at most "
                        + MAX_PREFIX_CHARS + " characters, got one of " + prefix.length());
            }
        }
        return prefixes;
    }
}
