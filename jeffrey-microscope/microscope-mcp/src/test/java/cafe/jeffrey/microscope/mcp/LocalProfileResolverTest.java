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

package cafe.jeffrey.microscope.mcp;

import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.persistence.api.ProfileRepository;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalProfileResolverTest {

    private static final String PROFILE_ID = "profile-1";

    @Mock
    MicroscopeCoreRepositories coreRepositories;

    @Mock
    ProfileRepository profileRepository;

    @Mock
    ProfileManager.Factory profileManagerFactory;

    @Test
    void resolvesARegisteredProfileThroughTheFactory() {
        ProfileInfo info = mock(ProfileInfo.class);
        ProfileManager manager = mock(ProfileManager.class);
        when(coreRepositories.newProfileRepository(PROFILE_ID)).thenReturn(profileRepository);
        when(profileRepository.find()).thenReturn(Optional.of(info));
        when(profileManagerFactory.apply(info)).thenReturn(manager);

        LocalProfileResolver resolver = new LocalProfileResolver(coreRepositories, profileManagerFactory);

        assertSame(manager, resolver.resolve(PROFILE_ID));
    }

    @Test
    void refusesAnUnknownProfileAsAClientError() {
        when(coreRepositories.newProfileRepository(PROFILE_ID)).thenReturn(profileRepository);
        when(profileRepository.find()).thenReturn(Optional.empty());

        LocalProfileResolver resolver = new LocalProfileResolver(coreRepositories, profileManagerFactory);

        assertThrows(JeffreyClientException.class, () -> resolver.resolve(PROFILE_ID));
    }
}
