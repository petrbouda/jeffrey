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

package pbouda.jeffrey.profile.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.LongFunction;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WeightExtractor factory methods")
class WeightExtractorTest {

    @Nested
    @DisplayName("duration() no-arg factory method")
    class DurationFormatterProducesTimeString {

        @Test
        @DisplayName("100_000_000 nanos (100ms) formats as a time string containing 'ms'")
        void hundredMillisecondsContainsMs() {
            WeightExtractor extractor = WeightExtractor.duration();
            LongFunction<String> formatter = extractor.formatter();

            String result = formatter.apply(100_000_000L);

            assertTrue(result.contains("ms") || result.contains("100"),
                    "Expected time string for 100ms, but got: " + result);
            assertFalse(result.contains("MB") || result.contains("KB") || result.contains("GB") || result.contains("iB"),
                    "Duration formatter should not produce byte units, but got: " + result);
        }

        @Test
        @DisplayName("1_000_000 nanos (1ms) formats as a time string containing 'ms'")
        void oneMillisecondContainsMs() {
            WeightExtractor extractor = WeightExtractor.duration();
            LongFunction<String> formatter = extractor.formatter();

            String result = formatter.apply(1_000_000L);

            assertTrue(result.contains("ms") || result.contains("1"),
                    "Expected time string for 1ms, but got: " + result);
            assertFalse(result.contains("MB") || result.contains("KB") || result.contains("GB") || result.contains("iB"),
                    "Duration formatter should not produce byte units, but got: " + result);
        }

        @Test
        @DisplayName("1_000 nanos (1us) does not contain byte units")
        void oneMicrosecondDoesNotContainByteUnits() {
            WeightExtractor extractor = WeightExtractor.duration();
            LongFunction<String> formatter = extractor.formatter();

            String result = formatter.apply(1_000L);

            assertFalse(result.contains("B") || result.contains("KB"),
                    "Duration formatter should not produce byte units for 1us, but got: " + result);
            assertTrue(result.contains("s") || result.contains("ns"),
                    "Expected time string for 1us, but got: " + result);
        }
    }

    @Nested
    @DisplayName("duration(entityClassField) factory method")
    class DurationWithEntityClassFieldFormatterProducesTimeString {

        @Test
        @DisplayName("Formatter from duration(entityClassField) also uses DurationUtils")
        void durationWithEntityFieldUsesDurationFormatter() {
            WeightExtractor extractor = WeightExtractor.duration("someField");
            LongFunction<String> formatter = extractor.formatter();

            String result = formatter.apply(100_000_000L);

            assertTrue(result.contains("ms") || result.contains("100"),
                    "Expected time string for 100ms, but got: " + result);
            assertFalse(result.contains("MB") || result.contains("KB") || result.contains("GB") || result.contains("iB"),
                    "Duration formatter should not produce byte units, but got: " + result);
        }

        @Test
        @DisplayName("1_000 nanos via duration(entityClassField) does not contain byte units")
        void durationWithEntityFieldOneMicrosecond() {
            WeightExtractor extractor = WeightExtractor.duration("someField");
            LongFunction<String> formatter = extractor.formatter();

            String result = formatter.apply(1_000L);

            assertFalse(result.contains("B") || result.contains("KB"),
                    "Duration formatter should not produce byte units for 1us, but got: " + result);
            assertTrue(result.contains("s") || result.contains("ns"),
                    "Expected time string for 1us, but got: " + result);
        }
    }
}
