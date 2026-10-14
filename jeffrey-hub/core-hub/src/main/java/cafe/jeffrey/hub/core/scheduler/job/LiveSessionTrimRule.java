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

import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RecordingStatus;
import cafe.jeffrey.hub.model.repository.RepositoryFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The closed chunks of a session still recording that are older than {@code retention}, so a
 * session that runs for weeks stays bounded by age. The chunk the profiler holds open is never
 * among them ({@link RecordingSession#finishedRecordings()} leaves it out); retained sessions are
 * left whole.
 */
record LiveSessionTrimRule(Duration retention) implements RetentionRule {

    private static final Logger LOG = LoggerFactory.getLogger(LiveSessionTrimRule.class);

    @Override
    public void apply(ProjectRetentionPass pass) {
        Instant cutoff = pass.now().minus(retention);
        for (RecordingSession session : pass.sessions()) {
            if (session.retained() || session.status() != RecordingStatus.ACTIVE) {
                continue;
            }
            List<RepositoryFile> expired = session.finishedRecordings().stream()
                    .filter(file -> file.createdAt().isBefore(cutoff))
                    .toList();
            if (!expired.isEmpty()) {
                pass.deleteFiles(session, expired);
                LOG.info("Deleted expired recordings from live session: project_name={} session_id={} count={}",
                        pass.projectName(), session.id(), expired.size());
            }
        }
    }
}
