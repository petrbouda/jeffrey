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

package cafe.jeffrey.volumescheduler.retention;

import cafe.jeffrey.volumescheduler.volume.ProjectInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * Abandoned instance directories: ones created more than {@code retention} ago that never got a
 * session — the provisioner registered them and nothing ever streamed. An instance whose sessions
 * retention deleted goes with its last session, so this is the only way an instance directory
 * outlives its data. It holds no session, so the pass's listing is unaffected. An instance whose
 * creation time the filesystem cannot tell is kept: nothing is deleted on a guess.
 */
record StaleInstanceRule(Duration retention) implements RetentionRule {

    private static final Logger LOG = LoggerFactory.getLogger(StaleInstanceRule.class);

    @Override
    public void apply(RetentionPass pass) {
        Instant cutoff = pass.now().minus(retention);
        for (ProjectInstance instance : pass.instances()) {
            boolean abandoned = instance.sessionCount() == 0
                    && instance.createdAt().map(createdAt -> createdAt.isBefore(cutoff)).orElse(false);
            if (abandoned && pass.deleteInstance(instance.id())) {
                LOG.info("Deleted an abandoned instance without sessions: {} instance_id={} created_at={}",
                        pass.logScope(), instance.id(), instance.createdAt().orElseThrow());
            }
        }
    }
}
