/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
