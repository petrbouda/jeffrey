/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.manager;

import pbouda.jeffrey.profile.heapdump.model.StringAnalysisReport;
import pbouda.jeffrey.profile.heapdump.model.ThreadAnalysisReport;
import pbouda.jeffrey.profile.manager.model.PerfCounter;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * No-op implementation of AdditionalFilesManager for Quick Analysis profiles.
 * Quick Analysis profiles don't have a project context, so additional files
 * (performance counters, heap dumps) are not available.
 */
public class NoOpAdditionalFilesManager implements AdditionalFilesManager {

    private final Path heapDumpAnalysisPath;

    public NoOpAdditionalFilesManager(JeffreyDirs jeffreyDirs, String profileId) {
        this.heapDumpAnalysisPath = jeffreyDirs.quickHeapDumpAnalysisDir(profileId);
    }

    @Override
    public void processAdditionalFiles(String recordingId) {
        // No-op: Quick Analysis doesn't have recording storage
    }

    @Override
    public boolean performanceCountersExists() {
        return false;
    }

    @Override
    public List<PerfCounter> performanceCounters() {
        return List.of();
    }

    @Override
    public boolean heapDumpExists() {
        return false;
    }

    @Override
    public Optional<Path> getHeapDumpPath() {
        return Optional.empty();
    }

    @Override
    public Path getHeapDumpAnalysisPath() {
        return heapDumpAnalysisPath;
    }

    @Override
    public boolean stringAnalysisExists() {
        return false;
    }

    @Override
    public Optional<StringAnalysisReport> getStringAnalysis() {
        return Optional.empty();
    }

    @Override
    public void saveStringAnalysis(StringAnalysisReport report) {
        // No-op: Quick Analysis doesn't support heap dump analysis
    }

    @Override
    public void deleteStringAnalysis() {
        // No-op: Quick Analysis doesn't support heap dump analysis
    }

    @Override
    public boolean threadAnalysisExists() {
        return false;
    }

    @Override
    public Optional<ThreadAnalysisReport> getThreadAnalysis() {
        return Optional.empty();
    }

    @Override
    public void saveThreadAnalysis(ThreadAnalysisReport report) {
        // No-op: Quick Analysis doesn't support heap dump analysis
    }

    @Override
    public void deleteThreadAnalysis() {
        // No-op: Quick Analysis doesn't support heap dump analysis
    }
}
