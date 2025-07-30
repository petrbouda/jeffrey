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

import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptor;
import pbouda.jeffrey.common.model.job.JobInfo;
import pbouda.jeffrey.common.model.job.JobType;
import pbouda.jeffrey.scheduler.job.descriptor.JobDescriptorFactory;

import java.util.List;
import java.util.Map;

public interface SchedulerManager {

    default void create(JobType jobType, Map<String, String> params) {
        create(JobDescriptorFactory.create(jobType, params));
    }

    void create(JobDescriptor<?> jobDescriptor);

    List<JobInfo> all();

    List<JobInfo> all(JobType jobType);

    void updateEnabled(String id, boolean enabled);

    void delete(String id);
}
