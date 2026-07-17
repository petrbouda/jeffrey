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

package cafe.jeffrey.provider.profile.api;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecordingFormatRegistryTest {

    private static final RecordingFormat PPROF_FORMAT =
            new StubFormat(RecordingEventSource.PPROF, SupportedRecordingFile.PPROF);
    private static final RecordingFormat DEFAULT_FORMAT =
            new StubFormat(RecordingEventSource.JDK, SupportedRecordingFile.JFR);

    private final RecordingFormatRegistry registry =
            RecordingFormatRegistry.of(List.of(PPROF_FORMAT), DEFAULT_FORMAT);

    @Nested
    class BySource {

        @Test
        void registeredSourceResolvesItsFormat() {
            assertSame(PPROF_FORMAT, registry.bySource(RecordingEventSource.PPROF));
        }

        @Test
        void unregisteredSourceFallsBackToDefault() {
            assertSame(DEFAULT_FORMAT, registry.bySource(RecordingEventSource.ASYNC_PROFILER));
            assertSame(DEFAULT_FORMAT, registry.bySource(RecordingEventSource.UNKNOWN));
        }

        @Test
        void nullSourceFallsBackToDefault() {
            assertSame(DEFAULT_FORMAT, registry.bySource(null));
        }
    }

    @Nested
    class ByFile {

        @Test
        void registeredFileTypeResolvesItsFormat() {
            assertSame(PPROF_FORMAT, registry.byFile(Path.of("recording.pprof")));
            assertSame(PPROF_FORMAT, registry.byFile(Path.of("recording.pb.gz")));
        }

        @Test
        void unregisteredFileTypeFallsBackToDefault() {
            assertSame(DEFAULT_FORMAT, registry.byFile(Path.of("recording.jfr")));
            assertSame(DEFAULT_FORMAT, registry.byFile(Path.of("unrecognized.bin")));
        }
    }

    @Nested
    class Registration {

        @Test
        void duplicateSourceRegistrationIsRejected() {
            RecordingFormat duplicate = new StubFormat(RecordingEventSource.PPROF, SupportedRecordingFile.PPROF);

            assertThrows(IllegalArgumentException.class,
                    () -> RecordingFormatRegistry.of(List.of(PPROF_FORMAT, duplicate), DEFAULT_FORMAT));
        }
    }

    @Nested
    class InformationParserView {

        @Test
        void dispatchesByFileType() {
            RecordingInformation pprofInfo = recordingInformation();
            RecordingFormat pprofWithParser = new StubFormat(
                    RecordingEventSource.PPROF, SupportedRecordingFile.PPROF, path -> pprofInfo);
            RecordingInformation defaultInfo = recordingInformation();
            RecordingFormat defaultWithParser = new StubFormat(
                    RecordingEventSource.JDK, SupportedRecordingFile.JFR, path -> defaultInfo);

            RecordingFormatRegistry withParsers =
                    RecordingFormatRegistry.of(List.of(pprofWithParser), defaultWithParser);

            assertEquals(pprofInfo, withParsers.informationParser().provide(Path.of("recording.pprof")));
            assertEquals(defaultInfo, withParsers.informationParser().provide(Path.of("recording.jfr")));
        }

        private RecordingInformation recordingInformation() {
            return new RecordingInformation(0, RecordingEventSource.UNKNOWN, null, null);
        }
    }

    private record StubFormat(
            RecordingEventSource eventSource,
            SupportedRecordingFile fileType,
            RecordingInformationParser informationParser) implements RecordingFormat {

        StubFormat(RecordingEventSource eventSource, SupportedRecordingFile fileType) {
            this(eventSource, fileType, null);
        }

        @Override
        public RecordingEventParser eventParser() {
            return null;
        }

        @Override
        public RecordingFormatCapabilities capabilities() {
            return new RecordingFormatCapabilities(true, true);
        }

        @Override
        public EventCategoryResolver eventCategoryResolver() {
            return null;
        }
    }
}
