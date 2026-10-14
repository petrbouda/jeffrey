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

package cafe.jeffrey.hub.core.scheduler.job;

import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo.ProjectInstanceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * Dead instances, with their directories: EXPIRED instances more than {@code retention} past their
 * expiry, and PENDING instances started more than {@code retention} ago that never streamed a
 * session — no lifecycle event would ever move those out of PENDING. Neither holds a session, so
 * the pass's listing is unaffected.
 */
record StaleInstanceRule(Duration retention) implements RetentionRule {

    private static final Logger LOG = LoggerFactory.getLogger(StaleInstanceRule.class);

    @Override
    public void apply(ProjectRetentionPass pass) {
        Instant cutoff = pass.now().minus(retention);

        for (ProjectInstanceInfo instance : pass.instances().findByStatus(ProjectInstanceStatus.EXPIRED)) {
            if (instance.expiredAt() != null && instance.expiredAt().isBefore(cutoff)) {
                pass.deleteInstance(instance.id());
                LOG.info("Deleted expired instance: project_name={} instance_id={} expired_at={}",
                        pass.projectName(), instance.id(), instance.expiredAt());
            }
        }

        for (ProjectInstanceInfo instance : pass.instances().findByStatus(ProjectInstanceStatus.PENDING)) {
            boolean abandoned = instance.startedAt() != null
                    && instance.startedAt().isBefore(cutoff)
                    && instance.sessionCount() == 0;
            if (abandoned) {
                pass.deleteInstance(instance.id());
                LOG.info("Deleted abandoned PENDING instance: project_name={} instance_id={} started_at={}",
                        pass.projectName(), instance.id(), instance.startedAt());
            }
        }
    }
}
