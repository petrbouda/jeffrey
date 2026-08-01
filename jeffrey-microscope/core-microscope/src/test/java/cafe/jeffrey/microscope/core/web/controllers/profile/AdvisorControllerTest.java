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

package cafe.jeffrey.microscope.core.web.controllers.profile;

import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.advisor.prompt.AdvisorPromptManagerFactory;
import cafe.jeffrey.profile.advisor.run.AdvisorRunner;
import cafe.jeffrey.profile.advisor.run.AdvisorStages;
import cafe.jeffrey.profile.advisor.settings.AdvisorSettingsResolver;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.provider.profile.api.PipelineRunRepository;
import cafe.jeffrey.provider.profile.api.ProfileAdvisorRepository;
import cafe.jeffrey.provider.profile.api.ProfilePersistenceProvider;
import cafe.jeffrey.provider.profile.api.ProfileRepositories;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import javax.sql.DataSource;

import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdvisorControllerTest {

    private static final String PROFILE_ID = "p-1";

    @Mock
    ProfileManagerResolver resolver;

    @Mock
    ProfileManager profileManager;

    @Mock
    ProfileInfo profileInfo;

    @Mock
    AdvisorRunner advisorRunner;

    @Mock
    AdvisorPromptManagerFactory promptManagerFactory;

    @Mock
    AdvisorSettingsResolver settingsResolver;

    @Mock
    DatabaseManagerResolver databaseManagerResolver;

    @Mock
    ProfilePersistenceProvider persistenceProvider;

    @Mock
    ProfileRepositories repositories;

    @Mock
    ProfileAdvisorRepository advisorRepository;

    @Mock
    PipelineRunRepository pipelineRunRepository;

    @Mock
    DataSource profileDb;

    private AdvisorController controller() {
        return new AdvisorController(
                resolver, advisorRunner, promptManagerFactory, settingsResolver,
                databaseManagerResolver, persistenceProvider);
    }

    private void profileResolves() {
        when(resolver.resolve(PROFILE_ID)).thenReturn(profileManager);
        when(profileManager.info()).thenReturn(profileInfo);
        when(databaseManagerResolver.open(profileInfo)).thenReturn(profileDb);
        when(persistenceProvider.repositories()).thenReturn(repositories);
    }

    @Nested
    @DisplayName("Clearing results")
    class DeleteResults {

        @Test
        @DisplayName("forgets the batch first, then drops the stored artifacts and the kept timeline")
        void clearsEverythingDerived() {
            when(advisorRunner.forget(PROFILE_ID)).thenReturn(true);
            profileResolves();
            when(repositories.newAdvisorRepository(profileDb)).thenReturn(advisorRepository);
            when(repositories.newPipelineRunRepository(profileDb)).thenReturn(pipelineRunRepository);

            MockMvcTester mvc = mockMvcTesterFor(controller());

            assertThat(mvc.post().uri("/api/internal/profiles/p-1/advisor/delete-results")).hasStatusOk();

            verify(advisorRepository).deleteAll();
            verify(pipelineRunRepository).deleteAll(AdvisorStages.PIPELINE_ID);
        }

        @Test
        @DisplayName("refuses while a run is in flight — forget is the atomic guard, so nothing is wiped")
        void refusesWhileRunning() {
            when(advisorRunner.forget(PROFILE_ID)).thenReturn(false);

            MockMvcTester mvc = mockMvcTesterFor(controller());

            assertThat(mvc.post().uri("/api/internal/profiles/p-1/advisor/delete-results"))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code").asString().isEqualTo("INVALID_REQUEST");

            verify(advisorRepository, never()).deleteAll();
            verify(pipelineRunRepository, never()).deleteAll(any());
        }
    }
}
