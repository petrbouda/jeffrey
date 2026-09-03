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

package cafe.jeffrey.microscope.core.manager.recordings;

import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.microscope.persistence.api.RecordingTagsRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordingCommitResolverTest {

    private static final String RECORDING_ID = "rec-1";

    @Mock
    RecordingTagsRepository recordingTagsRepository;

    private RecordingCommitResolver resolver() {
        return new RecordingCommitResolver(recordingTagsRepository);
    }

    @Nested
    class WithATag {

        @Test
        void readsJeffreysOwnTag() {
            when(recordingTagsRepository.listForRecording(RECORDING_ID))
                    .thenReturn(List.of(new RecordingTag("git.commit", "abc123")));

            assertEquals(Optional.of("abc123"), resolver().resolve(RECORDING_ID));
        }

        @Test
        void acceptsTheConventionalNamesOtherToolingWrites() {
            when(recordingTagsRepository.listForRecording(RECORDING_ID))
                    .thenReturn(List.of(new RecordingTag("org.opencontainers.image.revision", "def456")));

            assertEquals(Optional.of("def456"), resolver().resolve(RECORDING_ID));
        }

        @Test
        void prefersJeffreysTagWhenSeveralArePresent() {
            when(recordingTagsRepository.listForRecording(RECORDING_ID)).thenReturn(List.of(
                    new RecordingTag("vcs.revision", "other"),
                    new RecordingTag("GIT.COMMIT", "abc123")));

            assertEquals(Optional.of("abc123"), resolver().resolve(RECORDING_ID));
        }
    }

    @Nested
    class WithoutATag {

        @Test
        void answersEmptyRatherThanGuessing() {
            when(recordingTagsRepository.listForRecording(RECORDING_ID))
                    .thenReturn(List.of(new RecordingTag("build.number", "42")));

            assertTrue(resolver().resolve(RECORDING_ID).isEmpty());
        }

        @Test
        void ignoresABlankValue() {
            when(recordingTagsRepository.listForRecording(RECORDING_ID))
                    .thenReturn(List.of(new RecordingTag("git.commit", " ")));

            assertTrue(resolver().resolve(RECORDING_ID).isEmpty());
        }

        @Test
        void doesNotQueryForAMissingRecordingId() {
            assertTrue(resolver().resolve(null).isEmpty());
            assertTrue(resolver().resolve(" ").isEmpty());

            verifyNoInteractions(recordingTagsRepository);
        }
    }
}
