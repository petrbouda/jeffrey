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

import pbouda.jeffrey.model.JobInfo;
import pbouda.jeffrey.model.JobType;
import pbouda.jeffrey.repository.project.ProjectSchedulerRepository;

import java.util.List;
import java.util.Map;

public class SchedulerManagerImpl implements SchedulerManager {

    private final ProjectSchedulerRepository repository;

    public SchedulerManagerImpl(ProjectSchedulerRepository repository) {
        this.repository = repository;
    }

    @Override
    public void create(JobType repositoryType, Map<String, String> params) {
        repository.insert(new JobInfo(repositoryType, params));
    }

    @Override
    public List<JobInfo> all() {
        return repository.all();
    }

    @Override
    public void delete(String id) {
        repository.delete(id);
    }
}
