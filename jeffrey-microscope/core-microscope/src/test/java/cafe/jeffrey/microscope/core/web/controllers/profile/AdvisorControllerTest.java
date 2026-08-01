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
import cafe.jeffrey.profile.advisor.run.BatchAdvisorProgress;
import cafe.jeffrey.profile.advisor.run.BatchStatus;
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
import java.util.List;

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

    private static BatchAdvisorProgress batch(BatchStatus status) {
        return new BatchAdvisorProgress(PROFILE_ID, status, 0, 1, 0, null, null, List.of());
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
        @DisplayName("drops the stored artifacts, the kept timeline and the remembered batch")
        void clearsEverythingDerived() {
            when(advisorRunner.batchProgress(PROFILE_ID)).thenReturn(batch(BatchStatus.COMPLETED));
            profileResolves();
            when(repositories.newAdvisorRepository(profileDb)).thenReturn(advisorRepository);
            when(repositories.newPipelineRunRepository(profileDb)).thenReturn(pipelineRunRepository);

            MockMvcTester mvc = mockMvcTesterFor(controller());

            assertThat(mvc.post().uri("/api/internal/profiles/p-1/advisor/delete-results")).hasStatusOk();

            verify(advisorRepository).deleteAll();
            verify(pipelineRunRepository).deleteAll(AdvisorStages.PIPELINE_ID);
            // Without this the finished batch would outlive its results and keep reporting COMPLETED.
            verify(advisorRunner).forget(PROFILE_ID);
        }

        @Test
        @DisplayName("refuses while a run is in flight, rather than being overwritten moments later")
        void refusesWhileRunning() {
            when(advisorRunner.batchProgress(PROFILE_ID)).thenReturn(batch(BatchStatus.RUNNING));

            MockMvcTester mvc = mockMvcTesterFor(controller());

            assertThat(mvc.post().uri("/api/internal/profiles/p-1/advisor/delete-results"))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.code").asString().isEqualTo("INVALID_REQUEST");

            verify(advisorRepository, never()).deleteAll();
            verify(pipelineRunRepository, never()).deleteAll(any());
            verify(advisorRunner, never()).forget(any());
        }

        @Test
        @DisplayName("refuses while a run is merely queued, before any type has started")
        void refusesWhileQueued() {
            when(advisorRunner.batchProgress(PROFILE_ID)).thenReturn(batch(BatchStatus.QUEUED));

            MockMvcTester mvc = mockMvcTesterFor(controller());

            assertThat(mvc.post().uri("/api/internal/profiles/p-1/advisor/delete-results"))
                    .hasStatus(400);

            verify(advisorRunner, never()).forget(any());
        }
    }
}
