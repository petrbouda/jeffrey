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

import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.persistence.api.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.function.Consumer;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MicroscopeProfileCleanupTest {

    @Mock
    MicroscopeJeffreyDirs jeffreyDirs;

    @Mock
    MicroscopeCoreRepositories repositories;

    @Mock
    ProfileRepository profileRepository;

    @Mock
    Consumer<String> contextInvalidator;

    @TempDir
    Path tempDir;

    @Test
    void invalidatesBeforeAndAfterDeletingProfileStorage() {
        when(jeffreyDirs.profileDir("p-1")).thenReturn(tempDir.resolve("p-1"));
        when(repositories.newProfileRepository("p-1")).thenReturn(profileRepository);
        MicroscopeProfileCleanup cleanup =
                new MicroscopeProfileCleanup(jeffreyDirs, repositories, contextInvalidator);

        cleanup.deleteProfile("p-1");

        InOrder deletion = inOrder(contextInvalidator, profileRepository);
        deletion.verify(contextInvalidator).accept("p-1");
        deletion.verify(profileRepository).delete();
        deletion.verify(contextInvalidator).accept("p-1");
    }
}
