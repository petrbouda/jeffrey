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
package cafe.jeffrey.profile.heapdump.oql.function;

import java.util.regex.Pattern;

/**
 * One-entry cache for compiled {@link Pattern}s. OQL regex operands are
 * almost always loop-invariant (a string literal in the query), so caching
 * the last compiled pattern turns a per-heap-object {@code Pattern.compile}
 * into a single compilation per query — exactly the allocation pattern the
 * a hot loop warns about.
 *
 * <p>Thread-safe: the cached entry is an immutable record behind a volatile
 * read; concurrent callers with different regexes only lose the cache hit,
 * never correctness.
 */
public final class RegexPatternCache {

    private record CompiledRegex(String regex, Pattern pattern) {
    }

    private volatile CompiledRegex lastCompiled;

    /**
     * Returns a compiled {@link Pattern} for the given regex, reusing the
     * last compiled instance when the regex is unchanged.
     */
    public Pattern compile(String regex) {
        CompiledRegex cached = lastCompiled;
        if (cached != null && cached.regex().equals(regex)) {
            return cached.pattern();
        }
        Pattern pattern = Pattern.compile(regex);
        lastCompiled = new CompiledRegex(regex, pattern);
        return pattern;
    }
}
