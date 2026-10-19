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

package cafe.jeffrey.microscope.model;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A search over frame names, as the flamegraph search box and its context menu send it.
 * <p>
 * A name matches when it contains the pattern literally or when the pattern, read as a regular
 * expression, is found in it. The literal reading keeps a frame's own name matching itself although
 * it is no regular expression for itself: the context menu searches for the exact title, and
 * {@code Outer$Inner#run} or {@code byte[]} read as regular expressions match nothing or do not
 * compile. A pattern that does not compile is searched literally only. The flamegraph view in the UI
 * ({@code FrameSearchPattern.ts}) applies the same contract, so the highlighted frames and the
 * timeseries of one search agree.
 */
public final class SearchPattern {

    private final String literal;
    private final Pattern regex;

    private SearchPattern(String literal, Pattern regex) {
        this.literal = literal;
        this.regex = regex;
    }

    public static SearchPattern of(String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        return new SearchPattern(pattern, compile(pattern));
    }

    public boolean matches(String name) {
        return name.contains(literal) || (regex != null && regex.matcher(name).find());
    }

    /**
     * The pattern as typed, for the literal reading.
     */
    public String literal() {
        return literal;
    }

    /**
     * The pattern for the regular-expression reading, or {@code null} when it does not compile and
     * only the literal reading applies.
     */
    public String regexOrNull() {
        return regex != null ? regex.pattern() : null;
    }

    private static Pattern compile(String pattern) {
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }
}
