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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.IDGenerator;
import pbouda.jeffrey.provider.api.model.job.JobInfo;
import pbouda.jeffrey.provider.api.model.job.JobType;
import pbouda.jeffrey.provider.api.repository.SchedulerRepository;

import java.util.List;
import java.util.Map;

public class SchedulerManagerImpl implements SchedulerManager {

    private final SchedulerRepository repository;

    public SchedulerManagerImpl(SchedulerRepository repository) {
        this.repository = repository;
    }

    @Override
    public void create(JobType repositoryType, Map<String, String> params) {
        // It's not necessary to propagate Project ID it's already known by scheduler repository.
        repository.insert(new JobInfo(IDGenerator.generate(), null, repositoryType, params, true));
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
