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
package cafe.jeffrey.profile.heapdump.oql.function;

import java.util.regex.Pattern;

/**
 * One-entry cache for compiled {@link Pattern}s. OQL regex operands are
 * almost always loop-invariant (a string literal in the query), so caching
 * the last compiled pattern turns a per-heap-object {@code Pattern.compile}
 * into a single compilation per query — exactly the allocation pattern the
 * profile-guardian {@code RegexAllocGuard} warns about.
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
