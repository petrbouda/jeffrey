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

package cafe.jeffrey.microscope.core.manager.advisor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.microscope.persistence.api.AdvisorProjectFolder;
import cafe.jeffrey.microscope.persistence.api.AdvisorProjectFolderRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvisorProjectFoldersManagerTest {

    private static final Instant NOW = Instant.parse("2026-08-05T09:00:00Z");
    private static final String PRESENT_PATH = "/home/me/order-service";
    private static final String MISSING_PATH = "/home/me/gone";

    private InMemoryRepository repository;
    private AdvisorProjectFoldersManager manager;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        FolderProbe probe = path -> Set.of(PRESENT_PATH).contains(path);
        manager = new AdvisorProjectFoldersManager(repository, probe, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Nested
    class Create {

        @Test
        void assignsAnIdentityAndTheCurrentTime() {
            ProjectFolderStatus created = manager.create(new ProjectFolderDraft("order-service", PRESENT_PATH));

            assertFalse(created.folder().folderId().isBlank());
            assertEquals(NOW, created.folder().createdAt());
            assertEquals("order-service", created.folder().name());
        }

        @Test
        void reportsWhetherThePathResolves() {
            assertTrue(manager.create(new ProjectFolderDraft("order-service", PRESENT_PATH)).present());
            assertFalse(manager.create(new ProjectFolderDraft("gone", MISSING_PATH)).present());
        }

        @Test
        void rejectsANameAlreadyInUse() {
            manager.create(new ProjectFolderDraft("order-service", PRESENT_PATH));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> manager.create(new ProjectFolderDraft("order-service", MISSING_PATH)));

            assertTrue(ex.getMessage().contains("order-service"));
            assertEquals(1, repository.findAll().size());
        }
    }

    @Nested
    class Update {

        @Test
        void keepsTheOriginalCreationTime() {
            String folderId = manager.create(new ProjectFolderDraft("order-service", PRESENT_PATH))
                    .folder().folderId();

            Optional<ProjectFolderStatus> updated =
                    manager.update(folderId, new ProjectFolderDraft("Order Service", MISSING_PATH));

            assertTrue(updated.isPresent());
            assertEquals(NOW, updated.get().folder().createdAt());
            assertEquals("Order Service", updated.get().folder().name());
            assertEquals(MISSING_PATH, updated.get().folder().path());
        }

        // Saving an edit that did not touch the name must not trip the uniqueness check on itself.
        @Test
        void allowsAFolderToKeepItsOwnName() {
            String folderId = manager.create(new ProjectFolderDraft("order-service", PRESENT_PATH))
                    .folder().folderId();

            Optional<ProjectFolderStatus> updated =
                    manager.update(folderId, new ProjectFolderDraft("order-service", MISSING_PATH));

            assertTrue(updated.isPresent());
            assertEquals(MISSING_PATH, updated.get().folder().path());
        }

        @Test
        void rejectsANameHeldByAnotherFolder() {
            manager.create(new ProjectFolderDraft("order-service", PRESENT_PATH));
            String folderId = manager.create(new ProjectFolderDraft("payment-gateway", MISSING_PATH))
                    .folder().folderId();

            assertThrows(IllegalArgumentException.class,
                    () -> manager.update(folderId, new ProjectFolderDraft("order-service", MISSING_PATH)));
        }

        @Test
        void returnsEmptyForAnUnknownFolder() {
            assertTrue(manager.update("no-such-folder", new ProjectFolderDraft("x", PRESENT_PATH)).isEmpty());
        }
    }

    @Nested
    class Delete {

        @Test
        void removesAKnownFolder() {
            String folderId = manager.create(new ProjectFolderDraft("order-service", PRESENT_PATH))
                    .folder().folderId();

            assertTrue(manager.delete(folderId));
            assertTrue(manager.list().isEmpty());
        }

        @Test
        void reportsAnUnknownFolder() {
            assertFalse(manager.delete("no-such-folder"));
        }
    }

    @Nested
    class Drafts {

        @Test
        void trimSurroundingWhitespace() {
            ProjectFolderDraft draft = new ProjectFolderDraft("  order-service  ", "  " + PRESENT_PATH + "  ");

            assertEquals("order-service", draft.name());
            assertEquals(PRESENT_PATH, draft.path());
        }

        @Test
        void rejectABlankName() {
            assertThrows(IllegalArgumentException.class, () -> new ProjectFolderDraft("  ", PRESENT_PATH));
        }

        @Test
        void rejectABlankPath() {
            assertThrows(IllegalArgumentException.class, () -> new ProjectFolderDraft("order-service", " "));
        }

        // The Advisor resolves the path later, from a process whose working directory is not the user's.
        @Test
        void rejectARelativePath() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ProjectFolderDraft("order-service", "projects/order-service"));
        }
    }

    private static final class InMemoryRepository implements AdvisorProjectFolderRepository {

        private final List<AdvisorProjectFolder> folders = new ArrayList<>();

        @Override
        public List<AdvisorProjectFolder> findAll() {
            return folders.stream()
                    .sorted(Comparator.comparing(AdvisorProjectFolder::name))
                    .toList();
        }

        @Override
        public Optional<AdvisorProjectFolder> find(String folderId) {
            return folders.stream()
                    .filter(folder -> folder.folderId().equals(folderId))
                    .findFirst();
        }

        @Override
        public Optional<AdvisorProjectFolder> findByName(String name) {
            return folders.stream()
                    .filter(folder -> folder.name().equals(name))
                    .findFirst();
        }

        @Override
        public void insert(AdvisorProjectFolder folder) {
            folders.add(folder);
        }

        @Override
        public void update(AdvisorProjectFolder folder) {
            delete(folder.folderId());
            folders.add(folder);
        }

        @Override
        public void delete(String folderId) {
            folders.removeIf(folder -> folder.folderId().equals(folderId));
        }
    }
}
