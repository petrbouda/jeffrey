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

package cafe.jeffrey.server.core.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.model.job.JobInfo;
import cafe.jeffrey.shared.common.model.job.JobType;
import cafe.jeffrey.server.persistence.repository.SchedulerRepository;

import java.util.List;
import java.util.Map;

public class SchedulerManagerImpl implements SchedulerManager {
    private static final Logger LOG = LoggerFactory.getLogger(SchedulerManagerImpl.class);

    private final SchedulerRepository repository;

    public SchedulerManagerImpl(SchedulerRepository repository) {
        this.repository = repository;
    }

    @Override
    public void create(JobType jobType, Map<String, String> params) {
        LOG.debug("Creating job: type={}", jobType);

        List<JobInfo> current = all(jobType);
        if (!current.isEmpty()) {
            LOG.info("Job already exists. Not creating a new one: type={}", jobType);
            return;
        }

        JobInfo jobInfo = new JobInfo(IDGenerator.generate(), null, jobType, params, true);
        repository.insert(jobInfo);
    }

    @Override
    public List<JobInfo> all() {
        return repository.all();
    }

    @Override
    public List<JobInfo> all(JobType jobType) {
        return repository.allByJobType(jobType);
    }

    @Override
    public void updateEnabled(String id, boolean enabled) {
        LOG.debug("Updating job enabled: jobId={} enabled={}", id, enabled);
        repository.updateEnabled(id, enabled);
    }

    @Override
    public void delete(String id) {
        LOG.debug("Deleting job: jobId={}", id);
        repository.delete(id);
    }
}
