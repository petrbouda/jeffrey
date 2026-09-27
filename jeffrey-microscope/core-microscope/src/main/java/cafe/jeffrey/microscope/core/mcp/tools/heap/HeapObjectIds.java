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


package cafe.jeffrey.microscope.core.mcp.tools.heap;

/**
 * A heap object id as the {@code heap_} tools hand it out and take it back: a decimal string.
 * <p>
 * An HPROF object id is a 64-bit address, and a JSON number carries an integer exactly only up to
 * 2^53, so a client parsing ids as numbers would silently round them to a neighbouring object. Written
 * unsigned, as {@code profiles_viewLink} writes the id it places in a link, so a high address is one
 * decimal run of digits rather than a minus sign.
 */
public final class HeapObjectIds {

    /** The input name every heap tool takes an object id under. */
    public static final String PARAMETER = "objectId";

    private static final String REQUIRED = "objectId is required. Object ids come from "
            + "heap_browseClassInstances or heap_getDominatorTreeRoots.";
    private static final String NOT_DECIMAL = "objectId must be a decimal heap object id, as the heap tools "
            + "return it (for example \"4711\"): %s";

    private HeapObjectIds() {
    }

    public static String format(long objectId) {
        return Long.toUnsignedString(objectId);
    }

    /**
     * @return the id, or null for an entry that names no object
     */
    public static String formatNullable(Long objectId) {
        return objectId == null ? null : format(objectId);
    }

    /**
     * The id a caller passed, checked at the boundary so a malformed one is a correctable argument
     * error rather than a heap failure — and so an absent one is refused rather than unboxed to zero,
     * which would quietly inspect whichever object happens to be numbered nought.
     *
     * @throws IllegalArgumentException when the id is missing or not a decimal number
     */
    public static long parse(String objectId) {
        if (objectId == null || objectId.isBlank()) {
            throw new IllegalArgumentException(REQUIRED);
        }
        try {
            return Long.parseUnsignedLong(objectId.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(NOT_DECIMAL.formatted(objectId), e);
        }
    }
}
