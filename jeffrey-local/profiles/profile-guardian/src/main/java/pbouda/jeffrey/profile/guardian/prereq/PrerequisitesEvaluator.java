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

package pbouda.jeffrey.profile.guardian.prereq;

import pbouda.jeffrey.profile.common.analysis.AnalysisResult.Severity;
import pbouda.jeffrey.profile.guardian.GuardianResult;
import pbouda.jeffrey.profile.guardian.guard.Guard;
import pbouda.jeffrey.profile.guardian.guard.GuardAnalysisResult;
import pbouda.jeffrey.profile.guardian.preconditions.Preconditions;
import pbouda.jeffrey.shared.common.model.EventSummary;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;
import pbouda.jeffrey.shared.common.model.Type;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Produces data-quality checks that populate the "Prerequisites" panel on the Guardian page.
 * <p>
 * Unlike the frame-tree traversing guards, these are pure metadata inspections: they report on
 * the shape and quality of the recording itself so that the user understands why certain
 * downstream guards might be NA or why absolute-threshold heuristics are less reliable.
 */
public final class PrerequisitesEvaluator {

    private static final Duration SHORT_RECORDING_THRESHOLD = Duration.ofSeconds(60);

    private PrerequisitesEvaluator() {
    }

    public static List<GuardianResult> evaluate(ProfileInfo profileInfo, Preconditions preconditions) {
        List<GuardianResult> results = new ArrayList<>();
        results.add(eventSource(preconditions));
        results.add(recordingDuration(profileInfo));
        results.add(eventCoverage(preconditions));
        results.add(debugSymbols(preconditions));
        return results;
    }

    // ===== Event Source =====

    private static GuardianResult eventSource(Preconditions preconditions) {
        RecordingEventSource source = preconditions.eventSource();
        String label = source == null ? "Unknown" : source.toString();

        if (source == RecordingEventSource.ASYNC_PROFILER) {
            return build("Event Source", Severity.OK, label,
                    "Recording was produced by async-profiler.",
                    "JVM-internal guards (JIT, Safepoint, VM Operation, Deoptimization, GC-by-thread) require async-profiler's native stacks — those guards will run as normal.",
                    null);
        }
        if (source == RecordingEventSource.JDK) {
            return build("Event Source", Severity.INFO, label,
                    "Recording was produced by the JDK's built-in JFR (not async-profiler).",
                    "JVM-internal guards that rely on native stack frames (JIT, Safepoint, VM Operation, Deoptimization, per-GC attribution) will be Not Applicable on this recording. This is not an error — it is a capability limit of JDK JFR.",
                    "Re-record with async-profiler if you need JVM-internal guard coverage.");
        }
        return build("Event Source", Severity.INFO, label,
                "Recording source could not be determined from metadata.",
                "Several JVM-internal guards may be marked Not Applicable because their preconditions cannot be verified.",
                null);
    }

    // ===== Recording Duration =====

    private static GuardianResult recordingDuration(ProfileInfo profileInfo) {
        if (profileInfo.profilingStartedAt() == null || profileInfo.profilingFinishedAt() == null) {
            return build("Recording Duration", Severity.INFO, "unknown",
                    "Start/finish timestamps are missing on this profile.",
                    "Heuristics that rely on steady-state assumptions (e.g. JIT compilation CPU share) may misfire on extremely short recordings. Without timestamps we cannot tell.",
                    null);
        }
        Duration duration = Duration.between(profileInfo.profilingStartedAt(), profileInfo.profilingFinishedAt());
        String score = formatDuration(duration);

        if (duration.compareTo(SHORT_RECORDING_THRESHOLD) < 0) {
            return build("Recording Duration", Severity.WARNING, score,
                    "Recording is shorter than " + SHORT_RECORDING_THRESHOLD.toSeconds() + " seconds.",
                    "Sample-based heuristics are noisy on very short recordings. Warmup frames (JIT compilation in particular) dominate and can trigger false-positive warnings. Guard ratios should be interpreted with caution.",
                    "Capture a longer recording (ideally 2–10 minutes of steady-state traffic) for reliable analysis.");
        }
        return build("Recording Duration", Severity.OK, score,
                "Recording duration is sufficient for steady-state analysis.",
                null,
                null);
    }

    // ===== Event Coverage =====

    private static GuardianResult eventCoverage(Preconditions preconditions) {
        Set<String> presentGroups = classifyEventGroups(preconditions.eventTypes());
        String score = presentGroups.isEmpty() ? "none" : String.join(", ", presentGroups);

        if (presentGroups.isEmpty()) {
            return build("Event Coverage", Severity.WARNING, score,
                    "No Guardian-consumable event types are present in this recording.",
                    "Guardian analyses CPU samples, allocation samples, wall-clock samples, and blocking events. None of these are present here, so almost every guard will be Not Applicable.",
                    "Enable at least one of: jdk.ExecutionSample, jdk.ObjectAllocationSample, profiler.WallClockSample, jdk.JavaMonitorEnter.");
        }
        if (presentGroups.size() < 2) {
            return build("Event Coverage", Severity.INFO, score,
                    "Only " + presentGroups.size() + " of the four Guardian event groups is present.",
                    "Analysis will run for the available groups; the rest are simply Not Applicable. More groups → broader coverage.",
                    null);
        }
        return build("Event Coverage", Severity.OK, score,
                presentGroups.size() + " of 4 Guardian event groups are present.",
                null,
                null);
    }

    private static Set<String> classifyEventGroups(List<EventSummary> eventTypes) {
        Set<String> groups = new LinkedHashSet<>();
        if (eventTypes == null) {
            return groups;
        }
        for (EventSummary summary : eventTypes) {
            Type type = Type.fromCode(summary.name());
            if (type == Type.EXECUTION_SAMPLE) {
                groups.add("CPU");
            } else if (type == Type.OBJECT_ALLOCATION_SAMPLE
                    || type == Type.OBJECT_ALLOCATION_IN_NEW_TLAB
                    || type == Type.OBJECT_ALLOCATION_OUTSIDE_TLAB) {
                groups.add("Allocation");
            } else if (type == Type.WALL_CLOCK_SAMPLE) {
                groups.add("Wall-Clock");
            } else if (type == Type.JAVA_MONITOR_ENTER || type == Type.THREAD_PARK || type == Type.THREAD_SLEEP) {
                groups.add("Blocking");
            }
        }
        return groups;
    }

    // ===== Debug Symbols =====

    private static GuardianResult debugSymbols(Preconditions preconditions) {
        Boolean debug = preconditions.debugSymbolsAvailable();
        Boolean kernel = preconditions.kernelSymbolsAvailable();

        // Only async-profiler recordings have meaningful symbol metadata.
        if (preconditions.eventSource() != RecordingEventSource.ASYNC_PROFILER) {
            return build("Debug Symbols", Severity.OK, "n/a",
                    "Debug/kernel symbol flags are only relevant for async-profiler recordings.",
                    null,
                    null);
        }

        boolean debugOk = Boolean.TRUE.equals(debug);
        boolean kernelOk = Boolean.TRUE.equals(kernel);
        String score = (debugOk ? "debug=yes" : "debug=no") + " · " + (kernelOk ? "kernel=yes" : "kernel=no");

        if (debugOk && kernelOk) {
            return build("Debug Symbols", Severity.OK, score,
                    "Both debug and kernel symbols were available during profiling.",
                    null,
                    null);
        }
        return build("Debug Symbols", Severity.INFO, score,
                "Some symbol sources were unavailable during profiling.",
                "Missing debug symbols produce <code>[unknown_Java]</code>-style frames instead of real method names in JVM-internal parts of the stack. Missing kernel symbols hide syscall attribution. The guards still work on pure Java frames, but their flamegraphs and root-cause explanations may be degraded.",
                "Install JDK debug symbols (e.g. distro <code>-debuginfo</code> package) and/or run with a kernel that exposes <code>/proc/kallsyms</code>.");
    }

    // ===== Helpers =====

    private static GuardianResult build(String rule, Severity severity, String score,
                                        String summary, String explanation, String solution) {
        GuardAnalysisResult analysis = new GuardAnalysisResult(
                rule, severity, explanation, summary, solution, score,
                Guard.Category.PREREQUISITES, null);
        return GuardianResult.of(analysis);
    }

    private static String formatDuration(Duration d) {
        long totalSeconds = d.getSeconds();
        if (totalSeconds < 60) {
            return totalSeconds + "s";
        }
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes < 60) {
            return minutes + "m " + seconds + "s";
        }
        long hours = minutes / 60;
        long mins = minutes % 60;
        return hours + "h " + mins + "m";
    }
}
