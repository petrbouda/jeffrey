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


package cafe.jeffrey.shared.common.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentDigestTest {

    /** The published SHA-256 of the empty input, so the algorithm itself is pinned, not just its shape. */
    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void digestsTheEmptyInputToTheKnownValue() {
        assertEquals(EMPTY_SHA256, ContentDigest.sha256Hex(new byte[0]));
    }

    @Test
    void theStringOverloadDigestsUtf8Bytes() {
        String content = "asprof-settings = \"start,cpu\"\n";

        assertEquals(
                ContentDigest.sha256Hex(content.getBytes(StandardCharsets.UTF_8)),
                ContentDigest.sha256Hex(content));
    }

    @Test
    void equalContentDigestsEqually() {
        assertEquals(ContentDigest.sha256Hex("same"), ContentDigest.sha256Hex("same"));
    }

    @Test
    void differentContentDigestsDifferently() {
        assertNotEquals(ContentDigest.sha256Hex("one"), ContentDigest.sha256Hex("two"));
    }

    @Test
    void isLowerCaseHex() {
        assertEquals(ContentDigest.sha256Hex("anything").toLowerCase(), ContentDigest.sha256Hex("anything"));
        assertEquals(64, ContentDigest.sha256Hex("anything").length());
    }

    @Test
    void rejectsNullContent() {
        assertThrows(IllegalArgumentException.class, () -> ContentDigest.sha256Hex((String) null));
        assertThrows(IllegalArgumentException.class, () -> ContentDigest.sha256Hex((byte[]) null));
    }
}
