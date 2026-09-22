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


package cafe.jeffrey.hub.core.config;

import cafe.jeffrey.hub.model.config.ScopedConfigEntry;
import cafe.jeffrey.hub.model.config.ScopedConfigKey;
import cafe.jeffrey.hub.persistence.api.ScopedConfigRepository;
import cafe.jeffrey.shared.common.config.ConfigType;
import cafe.jeffrey.shared.common.config.ScopedConfigLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Adoption is what makes the shared volume the recoverable truth for configuration, as it already
 * is for projects, instances and sessions. Without it, a hub that came up on an empty database
 * would render every scope as empty and delete every workspace's configuration on its first tick.
 */
class ScopedConfigAdopterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-01-01T12:00:00Z"), ZoneOffset.UTC);
    private static final ScopedConfigKey KEY = ScopedConfigKey.workspace("ws-001");
    private static final String COMMAND = "-agentpath:/opt/lib.so=start,cpu";

    @TempDir
    Path scopeDir;

    private ScopedConfigRepository repository;
    private ScopeDirectories scopeDirectories;
    private ScopedConfigAdopter adopter;

    @BeforeEach
    void setUp() {
        repository = mock(ScopedConfigRepository.class);
        scopeDirectories = mock(ScopeDirectories.class);
        when(scopeDirectories.resolve(any())).thenReturn(Optional.of(scopeDir));
        adopter = new ScopedConfigAdopter(
                CLOCK, repository, scopeDirectories, new FilesystemScopedConfigPublisher());
    }

    private void publish(String content) throws IOException {
        Path file = ScopedConfigLayout.configFile(scopeDir);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void readsAFileBackWhenTheDatabaseKnowsNothing() throws IOException {
        when(repository.find(KEY)).thenReturn(List.of());
        publish("asprof-settings = \"" + COMMAND + "\"\n");

        assertTrue(adopter.adopt(KEY));

        ArgumentCaptor<ScopedConfigEntry> stored = ArgumentCaptor.forClass(ScopedConfigEntry.class);
        verify(repository).upsert(stored.capture());
        assertEquals(COMMAND, stored.getValue().value());
        assertEquals(ConfigType.ASPROF_SETTINGS, stored.getValue().type());
        assertEquals(KEY, stored.getValue().key());
    }

    /**
     * A stored value always wins. A file and a row that disagree cannot be ordered — a digest says
     * whether two things differ, never which came later — so the authored side is the one kept.
     */
    @Test
    void leavesAScopeAloneWhenTheDatabaseAlreadyHasIt() throws IOException {
        when(repository.find(KEY)).thenReturn(List.of(
                new ScopedConfigEntry(KEY, ConfigType.ASPROF_SETTINGS, "stored", CLOCK.instant())));
        publish("asprof-settings = \"from the file\"\n");

        assertFalse(adopter.adopt(KEY));

        verify(repository, never()).upsert(any());
    }

    @Test
    void adoptsNothingWhenThereIsNoFile() {
        when(repository.find(KEY)).thenReturn(List.of());

        assertFalse(adopter.adopt(KEY));

        verify(repository, never()).upsert(any());
    }

    /**
     * Reports the file as handled rather than adopting it: returning false would make the caller
     * publish over a file it could not read, which is how a hand-edit gets silently discarded.
     */
    @Test
    void leavesAnUnreadableFileAloneWithoutStoringAnything() throws IOException {
        when(repository.find(KEY)).thenReturn(List.of());
        publish("{ not hocon at all");

        assertTrue(adopter.adopt(KEY));

        verify(repository, never()).upsert(any());
    }

    @Test
    void leavesAFileAloneWhenAValueInItIsNotValid() throws IOException {
        when(repository.find(KEY)).thenReturn(List.of());
        publish("asprof-settings = \"<<JEFFREY:NOPE>>\"\n");

        assertTrue(adopter.adopt(KEY));

        verify(repository, never()).upsert(any());
    }

    @Test
    void adoptsNothingFromAFileWithNoKnownKeys() throws IOException {
        when(repository.find(KEY)).thenReturn(List.of());
        publish("something-else = \"value\"\n");

        assertFalse(adopter.adopt(KEY));

        verify(repository, never()).upsert(any());
    }

    @Test
    void adoptsNothingWhenTheScopeHasNoFolder() {
        when(repository.find(KEY)).thenReturn(List.of());
        when(scopeDirectories.resolve(any())).thenReturn(Optional.empty());

        assertFalse(adopter.adopt(KEY));
    }
}
