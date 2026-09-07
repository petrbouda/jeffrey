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
package cafe.jeffrey.microscope.core.mcp;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four switches an installation sets, and the one of them with a rule of its own: an empty family
 * list means every family rather than none, because that is what almost every installation wants and
 * the property exists for the few that do not.
 */
class ExternalMcpPropertiesTest {

    private static final String JFR = "jfr";
    private static final String HEAP = "heap";

    private static ExternalMcpProperties withFamilies(Set<String> families) {
        return new ExternalMcpProperties(true, true, true, families);
    }

    @Test
    void anEmptyFilterAdvertisesEveryFamily() {
        ExternalMcpProperties properties = withFamilies(Set.of());

        assertTrue(properties.advertises(JFR));
        assertTrue(properties.advertises(HEAP));
    }

    /**
     * An unset property binds to null rather than to an empty set, and a server that then refused
     * every family would answer an empty tool list to an installation that configured nothing.
     */
    @Test
    void treatsAnUnsetFilterAsEveryFamily() {
        ExternalMcpProperties properties = withFamilies(null);

        assertEquals(Set.of(), properties.families());
        assertTrue(properties.advertises(JFR));
    }

    @Test
    void advertisesOnlyTheNamedFamiliesWhenTheFilterIsSet() {
        ExternalMcpProperties properties = withFamilies(Set.of(JFR));

        assertTrue(properties.advertises(JFR));
        assertFalse(properties.advertises(HEAP));
    }

    /**
     * The record is read on every request that lists tools, so it must not be able to change under
     * one: the set it keeps is its own copy.
     */
    @Test
    void keepsItsOwnCopyOfTheFilter() {
        Set<String> mutable = new LinkedHashSet<>(Set.of(JFR));
        ExternalMcpProperties properties = withFamilies(mutable);

        mutable.add(HEAP);

        assertFalse(properties.advertises(HEAP));
        assertThrows(UnsupportedOperationException.class, () -> properties.families().add(HEAP));
    }
}
