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
import cafe.jeffrey.hub.model.repository.RepositoryFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bounds the disk one project occupies to {@code budgetBytes}, which the age rules cannot do: a
 * service writing gigabytes an hour fills the volume long before anything ages out.
 *
 * <p>Reclaims oldest-first in two stages: whole sessions that a newer one has ended, then closed
 * chunks of each instance's newest session. The recording that may still be on its way onto the
 * volume is never touched, so a project may stay a little over budget; that is logged rather than
 * forced.</p>
 */
record StorageQuotaRule(long budgetBytes) implements RetentionRule {

    private static final Logger LOG = LoggerFactory.getLogger(StorageQuotaRule.class);

    private static final Comparator<RecordingSession> OLDEST_FIRST = Comparator.comparing(RecordingSession::createdAt);

    StorageQuotaRule {
        if (budgetBytes <= 0) {
            throw new IllegalArgumentException("max-size must be positive: " + budgetBytes);
        }
    }

    @Override
    public void apply(ProjectRetentionPass pass) {
        long totalSize = pass.totalSizeBytes();
        if (totalSize <= budgetBytes) {
            return;
        }
        LOG.info("Project storage over budget, reclaiming oldest data: project_name={} total_bytes={} budget_bytes={}",
                pass.projectName(), totalSize, budgetBytes);

        reclaimEndedSessions(pass);
        if (pass.totalSizeBytes() > budgetBytes) {
            trimNewestSessions(pass);
        }

        long remaining = pass.totalSizeBytes();
        if (remaining > budgetBytes) {
            LOG.warn("Project still over storage budget after reclamation, remaining data is still being written: "
                    + "project_name={} total_bytes={} budget_bytes={}", pass.projectName(), remaining, budgetBytes);
        }
    }

    private void reclaimEndedSessions(ProjectRetentionPass pass) {
        List<RecordingSession> deletable = pass.sessions().stream()
                .filter(session -> !session.newest())
                .sorted(OLDEST_FIRST)
                .toList();

        for (RecordingSession session : deletable) {
            if (pass.totalSizeBytes() <= budgetBytes) {
                return;
            }
            if (pass.deleteSession(session)) {
                LOG.info("Deleted session to reclaim storage: project_name={} session_id={} freed_bytes={}",
                        pass.projectName(), session.id(), session.totalSizeBytes());
            }
        }
    }

    private void trimNewestSessions(ProjectRetentionPass pass) {
        List<RecordingSession> newest = pass.sessions().stream()
                .filter(RecordingSession::newest)
                .sorted(OLDEST_FIRST)
                .toList();

        for (RecordingSession session : newest) {
            long projected = pass.totalSizeBytes();
            if (projected <= budgetBytes) {
                return;
            }
            List<RepositoryFile> toDelete = new ArrayList<>();
            for (RepositoryFile file : session.trimmableRecordings()) {
                if (projected <= budgetBytes) {
                    break;
                }
                toDelete.add(file);
                projected -= file.size();
            }
            if (!toDelete.isEmpty()) {
                pass.deleteFiles(session, toDelete);
                LOG.info("Trimmed recordings from a newest session to reclaim storage: project_name={} session_id={} count={}",
                        pass.projectName(), session.id(), toDelete.size());
            }
        }
    }
}
