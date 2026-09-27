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

package cafe.jeffrey.flamegraph.export;

import cafe.jeffrey.frameir.Frame;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Which frames of one tree a search pattern hits, and which frames lead to a hit.
 * <p>
 * A frame matches when its name contains the pattern literally or when the pattern, read as a regular
 * expression, is found in it. The regular-expression reading is the one the flamegraph view in the UI
 * uses, so the same pattern marks the same frames in both; the literal one keeps a class name with a
 * {@code $} in it matching itself, which as a regular expression it would not.
 * <p>
 * The frames are held by identity: a {@link Frame} is a map, so two frames with equal children are
 * equal by {@code equals} while being different call paths.
 * <p>
 * The pattern may come from a model through {@code flamegraph_export}; it is compiled and run here,
 * locally, against frame names only — the same surface the UI's own search already offers — and is
 * refused above {@link #MAX_PATTERN_LENGTH} characters.
 */
final class FrameSearch {

    /** Longer than any frame name a search needs; a bound on what a caller can have compiled. */
    static final int MAX_PATTERN_LENGTH = 200;

    private static final FrameSearch NONE = new FrameSearch(null, name -> false);

    private final String pattern;
    private final Predicate<String> matcher;
    private final Set<Frame> onPathToMatch = Collections.newSetFromMap(new IdentityHashMap<>());
    private long matchedSamples;

    private FrameSearch(String pattern, Predicate<String> matcher) {
        this.pattern = pattern;
        this.matcher = matcher;
    }

    /**
     * The search over one tree, or {@link #NONE} when no pattern was given.
     *
     * @throws IllegalArgumentException when the pattern is longer than {@link #MAX_PATTERN_LENGTH}
     */
    static FrameSearch of(String pattern, Frame root) {
        if (pattern == null || pattern.isBlank()) {
            return NONE;
        }
        if (pattern.length() > MAX_PATTERN_LENGTH) {
            throw new IllegalArgumentException("search must be at most " + MAX_PATTERN_LENGTH
                    + " characters, got " + pattern.length() + ": search for a shorter part of the frame name");
        }
        FrameSearch search = new FrameSearch(pattern, matcher(pattern));
        search.walk(root);
        return search;
    }

    boolean active() {
        return pattern != null;
    }

    String pattern() {
        return pattern;
    }

    boolean matches(String frameName) {
        return frameName != null && matcher.test(frameName);
    }

    /**
     * Whether the frame matches or has a matching frame somewhere below it — the frames the export
     * keeps whatever the prune threshold says, so a small match is never pruned out of sight.
     */
    boolean leadsToMatch(Frame frame) {
        return onPathToMatch.contains(frame);
    }

    /**
     * The samples under the outermost matching frames. A match below a match is not counted again: its
     * samples are already inside the outer one's total.
     */
    long matchedSamples() {
        return matchedSamples;
    }

    private boolean walk(Frame frame) {
        boolean anyBelow = false;
        for (Map.Entry<String, Frame> entry : frame.entrySet()) {
            Frame child = entry.getValue();
            if (matches(entry.getKey())) {
                matchedSamples += child.totalSamples();
                walkWithoutCounting(child);
                onPathToMatch.add(child);
                anyBelow = true;
            } else if (walk(child)) {
                anyBelow = true;
            }
        }
        if (anyBelow) {
            onPathToMatch.add(frame);
        }
        return anyBelow;
    }

    /** Marks the paths to nested matches inside a subtree whose samples are already counted. */
    private void walkWithoutCounting(Frame frame) {
        for (Map.Entry<String, Frame> entry : frame.entrySet()) {
            Frame child = entry.getValue();
            walkWithoutCounting(child);
            if (matches(entry.getKey()) || onPathToMatch.contains(child)) {
                onPathToMatch.add(child);
            }
        }
    }

    private static Predicate<String> matcher(String pattern) {
        Predicate<String> literal = name -> name.contains(pattern);
        try {
            Pattern regex = Pattern.compile(pattern);
            return literal.or(name -> regex.matcher(name).find());
        } catch (PatternSyntaxException e) {
            return literal;
        }
    }
}
