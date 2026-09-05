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
package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.profile.common.pipeline.PipelineProgress;
import cafe.jeffrey.profile.common.pipeline.StageProgress;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpStages;
import cafe.jeffrey.profile.mcp.McpToolHints;
import cafe.jeffrey.profile.mcp.ToolParamValues;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * Builds what a heap dump has to have before it can be asked anything.
 * <p>
 * A parsed heap dump answers most questions from an index, and the expensive parts of that index —
 * the dominator tree, and the nine cached reports the UI's own pages render — were previously built
 * only by opening the profile in a browser. Over MCP the reports could be read and never computed, so
 * a reader working from a terminal met "has not been run yet" and had no way past it. That is the gap
 * these two tools close.
 * <p>
 * Both are asynchronous, and deliberately so. Building a dominator tree over a multi-gigabyte heap
 * takes minutes, which is well past the point where a client gives up on a tool call and retries. The
 * work runs on the same {@code heap-dump-init} pipeline the UI uses, so a run started here shows up in
 * the browser and vice versa, and a second request while one is in flight joins it rather than
 * starting a rival.
 * <p>
 * These are the only tools in the {@code heap_} family that write anything, and what they write is a
 * cache — no dump is altered and nothing is deleted. An installation that would rather not spend the
 * CPU withholds them with {@code jeffrey.microscope.mcp.compute.enabled=false}.
 */
public class HeapComputeMcpTools {

    private static final String HEAP_VIEW = "heap-dump/settings";

    private static final String STEP_STATUS =
            "heap_status reports how far it has got. The stages run in order, and each one's answer "
                    + "becomes readable as it completes rather than at the end.";
    private static final String STEP_DOMINATOR_FIRST =
            "Retained sizes come from the dominator stage. Until it completes they are missing rather "
                    + "than zero, so a ranking by retained size before then is empty for a reason.";
    private static final String STEP_ALREADY_RUNNING =
            "A run was already in flight for this profile, so this call joined it rather than starting "
                    + "a second one. The progress below is that run's.";

    private final ProfileManager profileManager;
    private final HeapDumpInitService initService;

    public HeapComputeMcpTools(ProfileManager profileManager, HeapDumpInitService initService) {
        this.profileManager = profileManager;
        this.initService = initService;
    }

    @Tool(description = "Build what this profile's heap dump needs before the reading tools can answer: "
            + "the index, the dominator tree that retained sizes come from, and the cached reports "
            + "(leak suspects, biggest objects, class-loader analysis, top consumers, string and "
            + "collection analysis). Call it once when heap_getLeakSuspects or any other report says it "
            + "has not been run yet, or when a ranking by retained size comes back empty. Returns "
            + "immediately with the stage list; the work continues in the background and heap_status "
            + "reports it. Pass a report name to compute just that one on a dump that is already "
            + "indexed. This is the one heap tool that writes, and what it writes is a cache.")
    @McpToolHints(readOnly = false)
    public String prepare(
            @ToolParam(required = false, description = "Compute only this report instead of all of them. "
                    + "Omit for a dump that has never been opened, which needs the whole pipeline")
            @ToolParamValues({"strings", "dominator", "threads", "biggest", "collections", "leaks",
                    "classloaders", "biggest-collections", "consumers", "duplicates"})
            String report) {

        HeapDumpManager heapDumpManager = requireHeapDump();
        String profileId = profileManager.info().id();
        boolean started = report == null || report.isBlank()
                ? initService.start(profileId, heapDumpManager, null)
                : initService.startReport(profileId, heapDumpManager, report.trim(), null);

        return LinkedOutput.json(new PrepareResult(
                started,
                report == null || report.isBlank() ? HeapDumpStages.REPORTS : List.of(report.trim()),
                stages(initService.progress(profileId)),
                nextSteps(started),
                UiLinks.view(profileId, HEAP_VIEW)));
    }

    @Tool(description = "How far heap_prepare has got on this profile: every stage with its state and, "
            + "once finished, how long it took. Poll it after heap_prepare rather than retrying the "
            + "report tool, which cannot tell 'still building' from 'never asked for'. A profile whose "
            + "dump was prepared in a previous session reports the last run rather than nothing.")
    public String status() {
        String profileId = profileManager.info().id();
        PipelineProgress progress = initService.progress(profileId);
        return LinkedOutput.json(new StatusResult(
                progress.state().name(),
                progress.isRunning(),
                progress.errorMessage(),
                stages(progress),
                UiLinks.view(profileId, HEAP_VIEW)));
    }

    /**
     * The heap dump this profile carries, or a refusal that says which family to use instead.
     * <p>
     * Checked here rather than left to fail inside the pipeline: a JFR recording asked to prepare a
     * heap dump would otherwise start a run that fails on its first stage, and the reader would read
     * the failure as a broken dump rather than as the wrong question.
     */
    private HeapDumpManager requireHeapDump() {
        HeapDumpManager heapDumpManager = profileManager.heapDumpManager();
        if (!heapDumpManager.heapDumpExists()) {
            throw new IllegalArgumentException(
                    "Profile " + profileManager.info().id() + " has no heap dump. Use profiles_features "
                            + "to see what a profile can answer; for a JFR recording use the jfr_, "
                            + "flamegraph_ and traces_ tools instead.");
        }
        return heapDumpManager;
    }

    private static List<Stage> stages(PipelineProgress progress) {
        return progress.stages().stream()
                .map(stage -> new Stage(stage.id(), stage.status().name(), stage.durationMs()))
                .toList();
    }

    private static List<String> nextSteps(boolean started) {
        return NextSteps.builder()
                .when(!started, STEP_ALREADY_RUNNING)
                .add(STEP_STATUS)
                .add(STEP_DOMINATOR_FIRST)
                .build();
    }

    /**
     * @param started false when a run was already in flight, which is not a failure — the caller is
     *                watching the same work either way, and saying so stops it from retrying
     */
    private record PrepareResult(
            boolean started,
            List<String> computing,
            List<Stage> stages,
            List<String> nextSteps,
            String uiLink) {
    }

    private record StatusResult(
            String state,
            boolean running,
            String errorMessage,
            List<Stage> stages,
            String uiLink) {
    }

    /**
     * @param durationMs null while the stage has not finished
     */
    private record Stage(String id, String status, Long durationMs) {
    }
}
