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

package cafe.jeffrey.tools.jfrotlp;

import io.opentelemetry.proto.profiles.v1development.ProfilesDictionary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DictionaryTest {

    @Test
    void indexZeroIsAlwaysTheZeroValue() {
        ProfilesDictionary dictionary = new Dictionary().build();

        // every table's index 0 must be the default (zero) value so a 0 reference means "not set"
        assertEquals("", dictionary.getStringTable(0));
        assertEquals(0, dictionary.getFunctionTable(0).getNameStrindex());
        assertEquals(0, dictionary.getLocationTable(0).getLinesCount());
        assertEquals(0, dictionary.getStackTable(0).getLocationIndicesCount());
        assertEquals(0, dictionary.getAttributeTable(0).getKeyStrindex());
        assertEquals(1, dictionary.getLinkTableCount());
        assertEquals(1, dictionary.getMappingTableCount());
    }

    @Test
    void stringsAreDeduplicatedAndStable() {
        Dictionary dictionary = new Dictionary();
        int first = dictionary.string("cpu");
        int second = dictionary.string("cpu");
        int other = dictionary.string("nanoseconds");

        assertEquals(first, second);
        assertNotEquals(first, other);
        assertTrue(first > 0);
    }

    @Test
    void functionsAreDeduplicatedByClassAndMethod() {
        Dictionary dictionary = new Dictionary();
        int a = dictionary.function("com.example.Foo", "bar");
        int b = dictionary.function("com.example.Foo", "bar");
        int c = dictionary.function("com.example.Foo", "baz");

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(2, dictionary.functionCount());
    }

    @Test
    void locationsAreDeduplicatedByFunctionLineAndFrameType() {
        Dictionary dictionary = new Dictionary();
        int fn = dictionary.function("com.example.Foo", "bar");
        int jvmAttr = dictionary.stringAttribute("profile.frame.type", "jvm");
        int nativeAttr = dictionary.stringAttribute("profile.frame.type", "native");

        int a = dictionary.location(fn, 42, jvmAttr);
        int b = dictionary.location(fn, 42, jvmAttr);
        int differentLine = dictionary.location(fn, 43, jvmAttr);
        int differentType = dictionary.location(fn, 42, nativeAttr);

        assertEquals(a, b);
        assertNotEquals(a, differentLine);
        assertNotEquals(a, differentType);
        assertEquals(3, dictionary.locationCount());
    }

    @Test
    void stacksAreDeduplicatedByContent() {
        Dictionary dictionary = new Dictionary();
        int a = dictionary.stack(List.of(3, 2, 1));
        int b = dictionary.stack(List.of(3, 2, 1));
        int other = dictionary.stack(List.of(1, 2, 3));

        assertEquals(a, b);
        assertNotEquals(a, other);
        assertEquals(2, dictionary.stackCount());
    }

    @Test
    void frameTypeAttributesAreDeduplicatedByKeyAndValue() {
        Dictionary dictionary = new Dictionary();
        int jvm1 = dictionary.stringAttribute("profile.frame.type", "jvm");
        int jvm2 = dictionary.stringAttribute("profile.frame.type", "jvm");
        int nativeAttr = dictionary.stringAttribute("profile.frame.type", "native");

        assertEquals(jvm1, jvm2);
        assertNotEquals(jvm1, nativeAttr);
    }
}
