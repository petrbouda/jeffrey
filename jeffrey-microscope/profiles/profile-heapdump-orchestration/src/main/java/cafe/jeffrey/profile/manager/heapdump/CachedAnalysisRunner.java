/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.profile.manager.heapdump;

import cafe.jeffrey.profile.manager.heapdump.analysis.CachedAnalysis;

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
            if (analysis.needsDominatorTree()) {
                session.buildDominatorTreeIfNeeded();
            }
            T result = analysis.compute(session.view());
            reports.write(analysis, result);
            return null;
        });
    }
}
