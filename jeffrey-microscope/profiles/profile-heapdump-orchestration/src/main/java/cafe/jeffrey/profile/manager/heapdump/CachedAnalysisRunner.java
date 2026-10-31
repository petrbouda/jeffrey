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

package cafe.jeffrey.profile.manager.heapdump;

import cafe.jeffrey.profile.manager.heapdump.analysis.CachedAnalysis;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Runs a {@link CachedAnalysis} end-to-end: opens a heap-dump session,
 * optionally builds the dominator tree, invokes the analysis, and persists
 * its result through {@link HeapDumpReportStore}.
 */
public final class CachedAnalysisRunner {

    private final HeapDumpSessionTemplate sessions;

    private final HeapDumpReportStore reports;

    public CachedAnalysisRunner(HeapDumpSessionTemplate sessions, HeapDumpReportStore reports) {
        this.sessions = sessions;
        this.reports = reports;
    }

    public <T> void run(CachedAnalysis<T> analysis) {
        sessions.execute(session -> {
            compute(session, analysis);
            return null;
        });
    }

    /**
     * Refresh an obsolete or unreadable stored report on access. A report never
     * requested before remains unprepared; it is not an empty report to invent.
     * The second check and computation share the session's per-dump lock, so
     * concurrent readers join the first reader's migrated result.
     * <p>
     * A read is a page load: it recomputes one report over the tree already there,
     * but never builds the dominator tree, which takes minutes on a large dump and
     * would hold the request and the dump's lock with no progress to show. A report
     * whose tree is missing or obsolete reads as not built, and Initialize (or
     * {@code heap_prepare}) rebuilds both as a tracked operation.
     */
    public <T> Optional<T> read(CachedAnalysis<T> analysis) {
        Optional<T> cached = reports.read(analysis);
        if (cached.isPresent() || !reports.hasStoredCopy(analysis)) {
            return cached;
        }
        return sessions.execute(session -> {
            Optional<T> current = reports.read(analysis);
            if (current.isPresent()) {
                return current.get();
            }
            if (!reports.hasStoredCopy(analysis)) {
                return null;
            }
            if (analysis.needsDominatorTree() && !session.view().hasDominatorTree()) {
                return null;
            }
            return compute(session, analysis);
        });
    }

    private <T> T compute(HeapDumpSession session, CachedAnalysis<T> analysis) throws SQLException, IOException {
        if (analysis.needsDominatorTree()) {
            session.buildDominatorTreeIfNeeded();
        }
        HeapReportVersion version = HeapReportVersion.from(analysis, session.view());
        T result = analysis.compute(session.view());
        reports.write(analysis, version, result);
        return result;
    }
}
