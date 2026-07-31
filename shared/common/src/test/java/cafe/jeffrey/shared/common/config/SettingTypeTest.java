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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingTypeTest {

    @Nested
    class Strings {

        @Test
        void anyValueIsAccepted() {
            assertTrue(SettingType.STRING.isValid("anything at all"));
        }

        @Test
        void emptyIsAcceptedBecauseUnsetSecretsAreStoredAsEmpty() {
            assertTrue(SettingType.STRING.isValid(""));
        }

        @Test
        void nullIsRejected() {
            assertFalse(SettingType.STRING.isValid(null));
        }
    }

    @Nested
    class PositiveInts {

        @Test
        void acceptsPositive() {
            assertTrue(SettingType.POSITIVE_INT.isValid("128000"));
        }

        @Test
        void acceptsSurroundingWhitespace() {
            assertTrue(SettingType.POSITIVE_INT.isValid("  120  "));
        }

        @Test
        void rejectsZero() {
            assertFalse(SettingType.POSITIVE_INT.isValid("0"));
        }

        @Test
        void rejectsNegative() {
            assertFalse(SettingType.POSITIVE_INT.isValid("-1"));
        }

        @Test
        void rejectsNonNumeric() {
            assertFalse(SettingType.POSITIVE_INT.isValid("abc"));
        }

        @Test
        void rejectsDecimal() {
            assertFalse(SettingType.POSITIVE_INT.isValid("1.5"));
        }

        @Test
        void rejectsEmpty() {
            assertFalse(SettingType.POSITIVE_INT.isValid(""));
        }
    }

    @Nested
    class Percentages {

        @Test
        void acceptsSmallFraction() {
            assertTrue(SettingType.PERCENTAGE.isValid("0.05"));
        }

        @Test
        void acceptsWholeNumber() {
            assertTrue(SettingType.PERCENTAGE.isValid("1.0"));
        }

        @Test
        void rejectsZeroBecauseAiExportConfigRequiresAnExclusiveRange() {
            assertFalse(SettingType.PERCENTAGE.isValid("0"));
        }

        @Test
        void rejectsHundred() {
            assertFalse(SettingType.PERCENTAGE.isValid("100"));
        }

        @Test
        void rejectsNegative() {
            assertFalse(SettingType.PERCENTAGE.isValid("-0.5"));
        }

        @Test
        void rejectsNonNumeric() {
            assertFalse(SettingType.PERCENTAGE.isValid("high"));
        }
    }

    @Nested
    class LogLevels {

        @Test
        void acceptsUpperCase() {
            assertTrue(SettingType.LOG_LEVEL.isValid("DEBUG"));
        }

        @Test
        void acceptsLowerCase() {
            assertTrue(SettingType.LOG_LEVEL.isValid("debug"));
        }

        @Test
        void acceptsOff() {
            assertTrue(SettingType.LOG_LEVEL.isValid("OFF"));
        }

        @Test
        void rejectsUnknownLevel() {
            assertFalse(SettingType.LOG_LEVEL.isValid("VERBOSE"));
        }
    }

    @Nested
    class AiProviders {

        @Test
        void acceptsNone() {
            assertTrue(SettingType.AI_PROVIDER.isValid("none"));
        }

        @Test
        void acceptsClaudeCode() {
            assertTrue(SettingType.AI_PROVIDER.isValid("claude-code"));
        }

        @Test
        void acceptsMixedCase() {
            assertTrue(SettingType.AI_PROVIDER.isValid("Claude"));
        }

        @Test
        void rejectsUnknownProvider() {
            assertFalse(SettingType.AI_PROVIDER.isValid("gemini"));
        }
    }

    @Nested
    class FrameTextModes {

        @Test
        void acceptsSingleLine() {
            assertTrue(SettingType.FRAME_TEXT_MODE.isValid("single-line"));
        }

        @Test
        void acceptsTwoLine() {
            assertTrue(SettingType.FRAME_TEXT_MODE.isValid("two-line"));
        }

        @Test
        void rejectsUnknownMode() {
            assertFalse(SettingType.FRAME_TEXT_MODE.isValid("three-line"));
        }
    }

    @Nested
    class KeyRegistry {

        @Test
        void declaredKeyResolvesToItsType() {
            assertEquals(SettingType.POSITIVE_INT,
                    MicroscopeSettingKeys.typeOf(MicroscopeSettingKeys.AI_MAX_TOKENS));
        }

        @Test
        void thresholdsArePercentages() {
            assertEquals(SettingType.PERCENTAGE,
                    MicroscopeSettingKeys.typeOf(MicroscopeSettingKeys.AI_EXPORT_MIN_FRAME_THRESHOLD_PCT));
        }

        @Test
        void undeclaredKeyFallsBackToString() {
            assertEquals(SettingType.STRING, MicroscopeSettingKeys.typeOf("jeffrey.unknown"));
        }
    }
}
