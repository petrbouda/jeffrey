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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Which values a {@link Traced} method may record as an attribute: an allow-list of types whose
 * {@code toString()} is part of what the type <em>is</em>, rather than something written for a
 * debugger.
 *
 * <h2>Why an allow-list and not a size limit</h2>
 * A captured argument goes through {@code String.valueOf} into a recording — a file that gets
 * uploaded, shared and kept. For a domain object that is the wrong thing to do three times over:
 * <ul>
 *   <li><b>It leaks.</b> A record's generated {@code toString()}, or Lombok's {@code @ToString},
 *       prints every component, so {@code Card[number=4111111111111111, cvv=123]} lands in the
 *       recording without anyone deciding it should. Truncating at a maximum length does not help;
 *       the first 256 characters of a card are still the card.</li>
 *   <li><b>It is unreadable.</b> A type without {@code toString()} renders as
 *       {@code com.acme.Card@1b6d3586} — an identity hash, unique per call, which is the worst
 *       possible key for a dashboard that groups spans by attribute value.</li>
 *   <li><b>It can cost more than the method.</b> {@code toString()} on a lazily loaded entity or a
 *       proxy can trigger a fetch or walk an unbounded object graph, on the hot path of the very
 *       call being measured.</li>
 * </ul>
 * The fix is a list of what may be recorded rather than of what may not, which is why capture is
 * refused by default and widened only by adding a type here.
 *
 * <h2>The list</h2>
 * <ul>
 *   <li>primitives and their boxes, {@code char}/{@link Character} included</li>
 *   <li>{@link CharSequence} — {@link String}, {@link StringBuilder} and anything else textual</li>
 *   <li>every {@code enum} constant, bodied constants included</li>
 *   <li>{@link UUID}</li>
 *   <li>{@link BigDecimal} and {@link BigInteger}</li>
 *   <li>the {@code java.time} value types — {@link Instant}, {@link LocalDate}, {@link LocalTime},
 *       {@link LocalDateTime}, {@link OffsetTime}, {@link OffsetDateTime}, {@link ZonedDateTime},
 *       {@link Duration}, {@link Period}, {@link Year}, {@link YearMonth}, {@link MonthDay},
 *       {@link ZoneOffset}</li>
 * </ul>
 * Deliberately not "primitives and Strings": an amount, an id, a timestamp and an enum are exactly
 * what belongs on a span, and each of them is an object whose textual form is stable, intentional
 * and cheap. What is excluded is the arbitrary domain object, not the non-primitive.
 *
 * <h2>Two moments, because one is not enough</h2>
 * A parameter's <em>declared</em> type settles the matter for {@code checkout(Card card)} — nothing
 * but a {@code Card} can arrive — so the refusal is decided once, when the method's metadata is
 * first read, and reported then. It settles nothing for {@code put(Object key)}, where a
 * {@link String} may well turn up at run time. {@link #mayHoldCapturableValue(Class)} answers the
 * first question and {@link #isCapturable(Class)} the second, and a value that reaches the recording
 * has passed both.
 *
 * @see Traced#includeMethodArgs()
 */
public final class CapturableTypes {

    /**
     * Recorded in place of a value whose type only turned out to be uncapturable at run time,
     * under a parameter whose declaration could not say so — {@code Object}, an interface, a type
     * variable. A named parameter that goes missing looks like a bug in the capture; this says the
     * value was asked for and refused.
     */
    public static final String UNSUPPORTED_VALUE = "<unsupported>";

    /**
     * The closed part of the list. The open parts — anything textual, any enum — are matched by
     * assignability below, so they are deliberately absent here.
     */
    private static final Set<Class<?>> SCALAR_TYPES = Set.of(
            boolean.class, byte.class, short.class, char.class,
            int.class, long.class, float.class, double.class,
            Boolean.class, Byte.class, Short.class, Character.class,
            Integer.class, Long.class, Float.class, Double.class,
            UUID.class, BigDecimal.class, BigInteger.class,
            Instant.class, LocalDate.class, LocalTime.class, LocalDateTime.class,
            OffsetTime.class, OffsetDateTime.class, ZonedDateTime.class,
            Duration.class, Period.class, Year.class, YearMonth.class, MonthDay.class, ZoneOffset.class);

    /**
     * Types a capturable value can still arrive under, so a declaration naming one of them decides
     * nothing. Every other supertype in play is an interface, which is covered without listing it.
     */
    private static final Set<Class<?>> OPEN_SUPERTYPES = Set.of(Object.class, Number.class);

    private CapturableTypes() {
    }

    /**
     * Whether a value of exactly this type may be recorded — the allow-list itself, asked of a
     * value's own class.
     */
    public static boolean isCapturable(Class<?> type) {
        // Enum.class rather than isEnum(): a constant with a body is an anonymous subclass, for
        // which isEnum() is false while the constant is plainly still an enum.
        return SCALAR_TYPES.contains(type)
                || CharSequence.class.isAssignableFrom(type)
                || Enum.class.isAssignableFrom(type);
    }

    /**
     * Whether a parameter declared with this type could ever supply a capturable value — the
     * question worth asking before a call is ever made, so a capture that can only ever be refused
     * is reported when the method is first read rather than silently every time it runs.
     * <p>
     * True for a capturable declaration, and for one that decides nothing: {@link Object}, a
     * {@link Number}, any interface ({@code Serializable}, {@code Comparable}, a domain interface),
     * and an erased type variable, which reaches here as its bound. False only when the declaration
     * itself rules capture out.
     */
    public static boolean mayHoldCapturableValue(Class<?> declaredType) {
        return isCapturable(declaredType)
                || declaredType.isInterface()
                || OPEN_SUPERTYPES.contains(declaredType);
    }
}
