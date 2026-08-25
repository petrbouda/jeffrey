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

package cafe.jeffrey.shared.common.jfr;

import tools.jackson.core.JsonGenerator;

import java.util.Arrays;

/**
 * One event's field values, held just long enough to decide what to do with them.
 * <p>
 * This exists because two things need the same values and used to each get their own pass: the JSON
 * that gets stored, and the choice of which value to pool out of it. Building a Jackson tree, then
 * walking it again to find the pooled field, then serializing it, meant three traversals and a node
 * object per field for every event in the recording. Values land here once instead, in parallel
 * primitive arrays so a long or a boolean costs no allocation at all, and both readers work off
 * them.
 * <p>
 * Reused across events by the mapper that owns it — the arrays grow to the widest event type seen
 * and then stop. One instance per parsing thread; nothing here is synchronized.
 */
final class FieldSlots {

    /** Which array a slot's value lives in. */
    private static final byte KIND_NULL = 0;
    private static final byte KIND_LONG = 1;
    private static final byte KIND_FLOAT = 2;
    private static final byte KIND_BOOLEAN = 3;
    private static final byte KIND_STRING = 4;

    private static final int INITIAL_CAPACITY = 32;

    /** No slot; what {@link #largestPoolableString(int)} returns when nothing qualifies. */
    static final int NO_SLOT = -1;

    private String[] names = new String[INITIAL_CAPACITY];
    private byte[] kinds = new byte[INITIAL_CAPACITY];
    private long[] longs = new long[INITIAL_CAPACITY];
    private float[] floats = new float[INITIAL_CAPACITY];
    private boolean[] booleans = new boolean[INITIAL_CAPACITY];
    private String[] strings = new String[INITIAL_CAPACITY];

    private int size;

    void reset() {
        size = 0;
    }

    void putNull(String name) {
        int slot = next(name);
        kinds[slot] = KIND_NULL;
    }

    void putLong(String name, long value) {
        int slot = next(name);
        kinds[slot] = KIND_LONG;
        longs[slot] = value;
    }

    /** A nullable long lands as an explicit JSON null, which is what a tree node did too. */
    void putLong(String name, Long value) {
        if (value == null) {
            putNull(name);
        } else {
            putLong(name, value.longValue());
        }
    }

    void putFloat(String name, float value) {
        int slot = next(name);
        kinds[slot] = KIND_FLOAT;
        floats[slot] = value;
    }

    void putBoolean(String name, boolean value) {
        int slot = next(name);
        kinds[slot] = KIND_BOOLEAN;
        booleans[slot] = value;
    }

    void putString(String name, String value) {
        if (value == null) {
            putNull(name);
            return;
        }
        int slot = next(name);
        kinds[slot] = KIND_STRING;
        strings[slot] = value;
    }

    String name(int slot) {
        return names[slot];
    }

    String string(int slot) {
        return strings[slot];
    }

    /**
     * The slot holding the largest string worth pooling, or {@link #NO_SLOT}.
     * <p>
     * Size alone decides, so no field name is known here: a statement's SQL, a written file's path
     * and a thread dump's result all qualify by the same rule, and an event type instrumented
     * tomorrow takes part with no change on this side. Ties keep the earlier slot, so the choice is
     * stable for a given event type rather than depending on iteration order.
     *
     * @param minLength the size at or above which pooling beats storing the text inline
     */
    int largestPoolableString(int minLength) {
        int largest = NO_SLOT;
        int largestLength = 0;
        for (int slot = 0; slot < size; slot++) {
            if (kinds[slot] != KIND_STRING) {
                continue;
            }
            int length = strings[slot].length();
            if (length >= minLength && length > largestLength) {
                largest = slot;
                largestLength = length;
            }
        }
        return largest;
    }

    /**
     * Writes the slots as a JSON object.
     *
     * @param skippedSlot the slot to leave out — the pooled one — or {@link #NO_SLOT} to write all
     */
    void writeJson(JsonGenerator generator, int skippedSlot) {
        generator.writeStartObject();
        for (int slot = 0; slot < size; slot++) {
            if (slot == skippedSlot) {
                continue;
            }
            generator.writeName(names[slot]);
            switch (kinds[slot]) {
                case KIND_LONG -> generator.writeNumber(longs[slot]);
                case KIND_FLOAT -> generator.writeNumber(floats[slot]);
                case KIND_BOOLEAN -> generator.writeBoolean(booleans[slot]);
                case KIND_STRING -> generator.writeString(strings[slot]);
                default -> generator.writeNull();
            }
        }
        generator.writeEndObject();
    }

    private int next(String name) {
        if (size == names.length) {
            grow();
        }
        names[size] = name;
        return size++;
    }

    private void grow() {
        int capacity = names.length * 2;
        names = Arrays.copyOf(names, capacity);
        kinds = Arrays.copyOf(kinds, capacity);
        longs = Arrays.copyOf(longs, capacity);
        floats = Arrays.copyOf(floats, capacity);
        booleans = Arrays.copyOf(booleans, capacity);
        strings = Arrays.copyOf(strings, capacity);
    }
}
