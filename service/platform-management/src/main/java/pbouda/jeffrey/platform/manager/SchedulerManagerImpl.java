/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.IDGenerator;
import pbouda.jeffrey.shared.model.job.JobInfo;
import pbouda.jeffrey.shared.model.job.JobType;
import pbouda.jeffrey.provider.api.repository.SchedulerRepository;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptor;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;

import java.util.List;
import java.util.Map;

public class SchedulerManagerImpl implements SchedulerManager {
    private static final Logger LOG = LoggerFactory.getLogger(SchedulerManagerImpl.class);

    private final SchedulerRepository repository;
    private final JobDescriptorFactory jobDescriptorFactory;

    public SchedulerManagerImpl(SchedulerRepository repository, JobDescriptorFactory jobDescriptorFactory) {
        this.repository = repository;
        this.jobDescriptorFactory = jobDescriptorFactory;
    }

    @Override
    public void create(JobType jobType, Map<String, String> params) {
        create(jobDescriptorFactory.create(jobType, params));
    }

    @Override
    public void create(JobDescriptor<?> jobDescriptor) {
        if (!jobDescriptor.allowMulti()) {
            List<JobInfo> current = all(jobDescriptor.type());
            if (!current.isEmpty()) {
                LOG.info("Job already exists. Not creating a new one: type={}", jobDescriptor.type());
                return;
            }
        }

        JobInfo jobInfo = new JobInfo(IDGenerator.generate(), null, jobDescriptor.type(), jobDescriptor.params(), true);
        repository.insert(jobInfo);
    }

    @Override
    public List<JobInfo> all() {
        return repository.all();
    }

    @Override
    public List<JobInfo> all(JobType jobType) {
        return repository.all().stream()
                .filter(jobInfo -> jobInfo.jobType() == jobType)
                .toList();
    }

    @Override
    public void updateEnabled(String id, boolean enabled) {
        repository.updateEnabled(id, enabled);
    }

    @Override
    public void delete(String id) {
        repository.delete(id);
    }
}
