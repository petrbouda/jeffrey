/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.platform.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptor;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.platform.scheduler.job.descriptor.WorkspaceEventsReplicatorJobDescriptor;
import pbouda.jeffrey.provider.platform.repository.SchedulerRepository;
import pbouda.jeffrey.shared.common.model.job.JobInfo;
import pbouda.jeffrey.shared.common.model.job.JobType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerManagerImplTest {

    @Mock
    private SchedulerRepository schedulerRepository;
    @Mock
    private JobDescriptorFactory jobDescriptorFactory;

    private SchedulerManagerImpl manager;

    @BeforeEach
    void setUp() {
        manager = new SchedulerManagerImpl(schedulerRepository, jobDescriptorFactory);
    }

    @Nested
    class CreateJobFromDescriptor {

        @Test
        void createsJob_whenAllowMultiIsFalse_andNoExistingJob() {
            JobDescriptor<?> descriptor = new WorkspaceEventsReplicatorJobDescriptor();
            when(schedulerRepository.all()).thenReturn(List.of());

            manager.create(descriptor);

            verify(schedulerRepository).insert(any(JobInfo.class));
        }

        @Test
        void skipsCreation_whenAllowMultiIsFalse_andJobAlreadyExists() {
            JobDescriptor<?> descriptor = new WorkspaceEventsReplicatorJobDescriptor();
            JobInfo existing = new JobInfo("id-1", null, JobType.WORKSPACE_EVENTS_REPLICATOR, Map.of(), true);
            when(schedulerRepository.all()).thenReturn(List.of(existing));

            manager.create(descriptor);

            verify(schedulerRepository, never()).insert(any());
        }

        // Note: allowMulti=true path is not tested because JobDescriptor is sealed
        // and no permitted implementation overrides allowMulti() to return true.
        // The default implementation always returns false.

        @Test
        void insertsJobInfo_withCorrectTypeAndParams() {
            JobDescriptor<?> descriptor = new WorkspaceEventsReplicatorJobDescriptor();
            when(schedulerRepository.all()).thenReturn(List.of());

            manager.create(descriptor);

            verify(schedulerRepository).insert(argThat(info ->
                    info.jobType() == JobType.WORKSPACE_EVENTS_REPLICATOR
                    && info.params().isEmpty()
                    && info.enabled()));
        }
    }

    @Nested
    class CreateJobFromType {

        @Test
        void delegatesToFactory_forJobTypeToDescriptorMapping() {
            JobDescriptor<?> descriptor = new WorkspaceEventsReplicatorJobDescriptor();
            when(jobDescriptorFactory.create(JobType.WORKSPACE_EVENTS_REPLICATOR, Map.of()))
                    .thenReturn(descriptor);
            when(schedulerRepository.all()).thenReturn(List.of());

            manager.create(JobType.WORKSPACE_EVENTS_REPLICATOR, Map.of());

            verify(jobDescriptorFactory).create(JobType.WORKSPACE_EVENTS_REPLICATOR, Map.of());
            verify(schedulerRepository).insert(any(JobInfo.class));
        }
    }

    @Nested
    class AllJobs {

        @Test
        void filtersJobsByType_correctly() {
            JobInfo job1 = new JobInfo("id-1", null, JobType.WORKSPACE_EVENTS_REPLICATOR, Map.of(), true);
            JobInfo job2 = new JobInfo("id-2", null, JobType.PROJECTS_SYNCHRONIZER, Map.of(), true);
            JobInfo job3 = new JobInfo("id-3", null, JobType.WORKSPACE_EVENTS_REPLICATOR, Map.of(), false);
            when(schedulerRepository.all()).thenReturn(List.of(job1, job2, job3));

            List<JobInfo> result = manager.all(JobType.WORKSPACE_EVENTS_REPLICATOR);

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(j -> j.jobType() == JobType.WORKSPACE_EVENTS_REPLICATOR));
        }

        @Test
        void returnsEmptyList_whenNoJobsOfRequestedType() {
            JobInfo job = new JobInfo("id-1", null, JobType.PROJECTS_SYNCHRONIZER, Map.of(), true);
            when(schedulerRepository.all()).thenReturn(List.of(job));

            List<JobInfo> result = manager.all(JobType.WORKSPACE_EVENTS_REPLICATOR);

            assertTrue(result.isEmpty());
        }
    }
}
