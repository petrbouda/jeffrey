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

package cafe.jeffrey.shared.volume;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A location on the volume, relative to its root: a list of plain path elements. It can name
 * nothing outside the volume, because no element may be empty, {@code .}, {@code ..} or hold a
 * separator, so a backend maps it onto its own namespace (a directory tree, a blob prefix) without
 * checking it again.
 *
 * @param segments the path elements from the root down; empty for the root itself
 */
public record VolumePath(List<String> segments) {

    private static final String SEPARATOR = "/";
    private static final String NUL = "\0";
    private static final String HIDDEN_PREFIX = ".";
    private static final Set<String> RELATIVE_ELEMENTS = Set.of(".", "..");

    private static final VolumePath ROOT = new VolumePath(List.of());

    public VolumePath {
        if (segments == null) {
            throw new IllegalArgumentException("A volume path needs its segments");
        }
        for (String segment : segments) {
            if (!isPlainElement(segment)) {
                throw new IllegalArgumentException("Not a plain path element of a volume path: segment=" + segment);
            }
        }
        segments = List.copyOf(segments);
    }

    public static VolumePath root() {
        return ROOT;
    }

    /**
     * The child of this path named {@code name}.
     *
     * @throws IllegalArgumentException when {@code name} is not one plain path element
     */
    public VolumePath resolve(String name) {
        List<String> child = new ArrayList<>(segments);
        child.add(name);
        return new VolumePath(child);
    }

    /**
     * The visible child named by an id that arrived from outside, or empty when the id is not one
     * plain, visible path element: an id must neither walk out of this path nor name the volume's
     * own bookkeeping, which is what a leading dot marks.
     */
    public Optional<VolumePath> visibleChild(String name) {
        if (!isPlainElement(name) || name.startsWith(HIDDEN_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(resolve(name));
    }

    /** The last element; empty for the root. */
    public String name() {
        return segments.isEmpty() ? "" : segments.getLast();
    }

    /**
     * The path one level up.
     *
     * @throws IllegalStateException for the root, which has no parent
     */
    public VolumePath parent() {
        if (segments.isEmpty()) {
            throw new IllegalStateException("The root of a volume has no parent");
        }
        return new VolumePath(segments.subList(0, segments.size() - 1));
    }

    /** Whether the last element is the volume's own bookkeeping, marked by a leading dot. */
    public boolean isHidden() {
        return name().startsWith(HIDDEN_PREFIX);
    }

    private static boolean isPlainElement(String name) {
        return name != null
                && !name.isBlank()
                && !RELATIVE_ELEMENTS.contains(name)
                && !name.contains(SEPARATOR)
                && !name.contains(NUL);
    }

    @Override
    public String toString() {
        return String.join(SEPARATOR, segments);
    }
}
