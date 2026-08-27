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

package cafe.jeffrey.jfr.events.trace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The line between a value whose textual form is part of what it is and a domain object whose
 * {@code toString()} was written for a debugger — asserted from both sides, because a list of what
 * may be recorded is only as good as what it keeps out.
 */
class CapturableTypesTest {

    @Nested
    @DisplayName("What may be recorded")
    class Capturable {

        @Test
        @DisplayName("primitives and their boxes, on both sides of the boxing")
        void primitives() {
            assertTrue(CapturableTypes.isCapturable(int.class));
            assertTrue(CapturableTypes.isCapturable(long.class));
            assertTrue(CapturableTypes.isCapturable(double.class));
            assertTrue(CapturableTypes.isCapturable(boolean.class));
            assertTrue(CapturableTypes.isCapturable(char.class));

            // A declaration says int; the argument array carries an Integer. Both must pass, or
            // capture would depend on which side of the call is asking.
            assertTrue(CapturableTypes.isCapturable(Integer.class));
            assertTrue(CapturableTypes.isCapturable(Long.class));
            assertTrue(CapturableTypes.isCapturable(Double.class));
            assertTrue(CapturableTypes.isCapturable(Boolean.class));
            assertTrue(CapturableTypes.isCapturable(Character.class));
        }

        @Test
        @DisplayName("anything textual, not only String")
        void text() {
            assertTrue(CapturableTypes.isCapturable(String.class));
            assertTrue(CapturableTypes.isCapturable(StringBuilder.class));
            assertTrue(CapturableTypes.isCapturable(CharSequence.class));
        }

        @Test
        @DisplayName("every enum constant, including one with a body")
        void enums() {
            assertTrue(CapturableTypes.isCapturable(SpanKind.class));
            assertTrue(CapturableTypes.isCapturable(SpanStatus.class));

            // A constant with a body is an anonymous subclass, for which isEnum() is false while
            // the constant is plainly still an enum -- which is why the check is by assignability.
            assertFalse(Bodied.WITH_BODY.getClass().isEnum());
            assertTrue(CapturableTypes.isCapturable(Bodied.WITH_BODY.getClass()));
        }

        @Test
        @DisplayName("the value types an amount, an id or a timestamp actually uses")
        void valueTypes() {
            assertTrue(CapturableTypes.isCapturable(UUID.class));
            assertTrue(CapturableTypes.isCapturable(BigDecimal.class));
            assertTrue(CapturableTypes.isCapturable(BigInteger.class));
            assertTrue(CapturableTypes.isCapturable(Instant.class));
            assertTrue(CapturableTypes.isCapturable(LocalDate.class));
            assertTrue(CapturableTypes.isCapturable(ZonedDateTime.class));
            assertTrue(CapturableTypes.isCapturable(Duration.class));
        }
    }

    @Nested
    @DisplayName("What may not")
    class Uncapturable {

        @Test
        @DisplayName("a domain object, whatever its toString() would have produced")
        void domainObjects() {
            assertFalse(CapturableTypes.isCapturable(Card.class),
                    "a record's generated toString() prints every component -- the card number "
                            + "included -- which is the whole reason for the list");
            assertFalse(CapturableTypes.isCapturable(Object.class));
            assertFalse(CapturableTypes.isCapturable(byte[].class));
        }

        @Test
        @DisplayName("a collection, which is a payload rather than a fact about the call")
        void collections() {
            assertFalse(CapturableTypes.isCapturable(java.util.List.class));
            assertFalse(CapturableTypes.isCapturable(java.util.Map.class));
        }
    }

    @Nested
    @DisplayName("Whether the declaration settles it")
    class DeclaredTypes {

        @Test
        @DisplayName("a capturable declaration settles it, and so does an uncapturable one")
        void decisiveDeclarations() {
            assertTrue(CapturableTypes.mayHoldCapturableValue(String.class));
            assertTrue(CapturableTypes.mayHoldCapturableValue(int.class));
            assertTrue(CapturableTypes.mayHoldCapturableValue(UUID.class));

            assertFalse(CapturableTypes.mayHoldCapturableValue(Card.class),
                    "nothing but a Card can arrive, so this is knowable before any call is made");
            assertFalse(CapturableTypes.mayHoldCapturableValue(byte[].class));
        }

        @Test
        @DisplayName("an open declaration settles nothing, so the value has to")
        void openDeclarations() {
            assertTrue(CapturableTypes.mayHoldCapturableValue(Object.class),
                    "put(Object key) may well be handed a String");
            assertTrue(CapturableTypes.mayHoldCapturableValue(Number.class),
                    "Number is the supertype of Integer and BigDecimal alike");
            assertTrue(CapturableTypes.mayHoldCapturableValue(Serializable.class));
            assertTrue(CapturableTypes.mayHoldCapturableValue(Comparable.class));
            assertTrue(CapturableTypes.mayHoldCapturableValue(Runnable.class),
                    "an interface is never decisive: refusing here would drop a parameter that an "
                            + "erased type variable had reached as its bound");
        }
    }

    private record Card(String number, String cvv) {
    }

    private enum Bodied {

        WITH_BODY {
            @Override
            String describe() {
                return "bodied";
            }
        };

        abstract String describe();
    }
}
