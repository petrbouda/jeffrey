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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * Abandoned instance directories: ones started more than {@code retention} ago that never got a
 * session — the provisioner registered them and nothing ever streamed. An instance
 * whose sessions retention deleted goes with its last session, so this is the only way an
 * instance directory outlives its data. It holds no session, so the pass's listing is unaffected.
 */
record StaleInstanceRule(Duration retention) implements RetentionRule {

    private static final Logger LOG = LoggerFactory.getLogger(StaleInstanceRule.class);

    @Override
    public void apply(ProjectRetentionPass pass) {
        Instant cutoff = pass.now().minus(retention);
        for (ProjectInstanceInfo instance : pass.instances()) {
            boolean abandoned = instance.sessionCount() == 0
                    && instance.startedAt().filter(startedAt -> startedAt.isBefore(cutoff)).isPresent();
            if (abandoned) {
                pass.deleteInstance(instance.id());
                LOG.info("Deleted an abandoned instance without sessions: project_name={} instance_id={} started_at={}",
                        pass.projectName(), instance.id(), instance.startedAt().orElse(null));
            }
        }
    }
}
