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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.manager.ide.IdeBridge;
import cafe.jeffrey.microscope.core.manager.ide.IdeOpenRequest;
import cafe.jeffrey.microscope.core.manager.ide.IdeOpenResult;
import cafe.jeffrey.microscope.core.manager.ide.IdeResolveRequest;
import cafe.jeffrey.microscope.core.manager.ide.IdeResolveResult;
import cafe.jeffrey.microscope.core.manager.ide.IdeSourceRequest;
import cafe.jeffrey.microscope.core.manager.ide.IdeSourceResult;
import cafe.jeffrey.microscope.core.manager.ide.IdeTarget;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetStatus;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetsResult.IdeInstanceView;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetsResult.IdeProjectView;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetsResult;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolHints;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.McpToolRequirement;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where the code behind a frame actually lives, answered by the developer's own IDE.
 * <p>
 * Every other family in this server ends at a method signature. The exports say so themselves: call
 * paths, figures, and at most a line number where every sample at a frame agreed on one — never a
 * file. That leaves a reader who wants to act on a finding grepping a checkout for a name that may be
 * inherited, overloaded, generated, or a Kotlin facade that exists under a different name on disk. This family closes that gap by asking the thing
 * that already knows: an IntelliJ window with the project open, its indexes built, and its sources
 * attached for the libraries too.
 * <p>
 * <strong>Resolving is not jumping.</strong> {@code ide_resolve} finds a location and reports it;
 * {@code ide_open} moves the developer's cursor. They are separate tools because they are separate
 * acts, and only one of them is safe to do a hundred times while writing up an analysis. A reader
 * grounding a finding wants the first; only an explicit "show me this" wants the second.
 * <p>
 * <strong>A location is reported with its caveats or not at all.</strong> {@code decompiled},
 * {@code imprecise} and {@code stale} travel with every answer, because the difference between a line
 * a finding can cite and one it cannot is exactly those three facts, and a bare path implies a
 * certainty the IDE did not offer.
 * <p>
 * <strong>The window is chosen once, and only when it is unambiguous.</strong> There is no reader at
 * the other end of an MCP call to answer a picker, so a first lookup links the single window that
 * contains the class and otherwise refuses with the candidates listed. Guessing between two checkouts
 * is how an analysis ends up quoting the wrong repository.
 * <p>
 * <strong>No UI links here.</strong> What these tools describe lives in an editor rather than in a
 * Jeffrey view, so there is nothing for {@code UiLinks} to point at; the answers are records without a
 * {@code uiLink}, and the tools are on the enforcement test's no-page list.
 * <p>
 * <strong>Structured where there is a record.</strong> Windows, a link, a resolved location and an
 * opened one are records. {@code ide_source} answers the class's text, which is a document to read
 * rather than a record, so it stays text.
 */
public class IdeMcpTools {

    private static final String NOT_LINKED =
            "No IDE window is linked to this profile yet, and the right one could not be picked "
                    + "unambiguously.";

    private static final String NO_IDE_RUNNING =
            "No IntelliJ IDEA window with the Jeffrey plugin is answering on this machine. The plugin "
                    + "is installed from the JetBrains Marketplace as \"Jeffrey Microscope\" and can be "
                    + "switched off under Settings -> Tools -> Jeffrey Plugin; a disabled IDE "
                    + "is invisible here. Without one, map frames to code by reading the checkout "
                    + "directly.";

    private static final String NOT_SELECTABLE =
            "This installation is configured to use the third-party JFR Profiler plugin "
                    + "(jeffrey.microscope.ide.mode=jfr-profiler-plugin), which serves a single IDE and "
                    + "cannot list or choose windows. ide_source and ide_open still work; ide_windows "
                    + "and ide_link do not apply.";

    /** Enough of a commit to identify it in a table, and short enough not to dominate the row. */
    private static final int SHORT_COMMIT_LENGTH = 12;

    private static final String IDE_WINDOWS = "ide_windows";
    private static final String IDE_LINK = "ide_link";
    private static final String IDE_SOURCE = "ide_source";
    private static final String PROFILES_GET = "profiles_get";
    private static final String PROFILE_ID = "profileId";
    private static final String PROJECT_ID = "projectId";
    private static final String CLASS_NAME = "className";

    private static final String LINK_WHY = "links the only window that contains the class to this profile";
    private static final String WINDOWS_WHY = "shows the branch and commit of each window, the surest way to tell "
            + "them apart";
    private static final String SOURCE_WHY = "reads the class as the IDE has it, since these line numbers are a "
            + "decompiler's";
    private static final String COMMIT_WHY = "names the commit the recording was tagged with, to check against the "
            + "checkout";

    private static final String CHOOSE_GUIDANCE =
            "Call ide_link with the projectId of the window that matches this profile, then repeat the lookup.";
    private static final String LINK_GUIDANCE =
            "Pass a projectId to ide_link to bind one of these windows to this profile. A null hasClass means "
                    + "no class name was given, not that the class is missing.";
    private static final String NO_COMMIT_GUIDANCE =
            "The recording carries no commit tag, so sameCommitAsRecording is null throughout and no window can "
                    + "be confirmed as the profiled build.";
    private static final String MOVED_ON_GUIDANCE =
            "The recording was built from commit %s; a window with sameCommitAsRecording false has moved on "
                    + "from it, and frames may not match what is on disk there.";
    private static final String LINKED_GUIDANCE = "ide_resolve, ide_source and ide_open now answer from that window.";
    private static final String DECOMPILED_GUIDANCE =
            "This file is decompiled - the line is the decompiler's, not the library's source. Cite the method, "
                    + "not the line.";
    private static final String IMPRECISE_GUIDANCE =
            "The position is the declaration rather than the requested line: read the method and locate the "
                    + "statement before citing a line.";
    private static final String STALE_GUIDANCE =
            "The file has been edited well after the recording was taken, so this line may describe code that "
                    + "no longer exists. Read it before citing it.";
    private static final String READ_GUIDANCE = "Read the file at this line before describing what it does.";

    private final IdeBridge ideBridge;
    private final ProfileManager profileManager;
    private final RecordingCommitResolver recordingCommitResolver;
    private final String profileId;
    private final AdvertisedFamilies advertised;

    public IdeMcpTools(
            IdeBridge ideBridge,
            ProfileManager profileManager,
            RecordingCommitResolver recordingCommitResolver,
            String profileId,
            AdvertisedFamilies advertised) {

        this.ideBridge = ideBridge;
        this.profileManager = profileManager;
        this.recordingCommitResolver = recordingCommitResolver;
        this.profileId = profileId;
        this.advertised = advertised;
    }
    @Tool(description = "Returns the IntelliJ windows open on this machine, which of them contains a "
            + "given class, and which one this profile is linked to, with the branch and commit each "
            + "window is on against the commit the recording was tagged with - so a checkout that "
            + "has moved on from the profiled build is visible. Settles a window ide_resolve reports "
            + "as ambiguous. status NOT_SELECTABLE: the configured IDE bridge serves one IDE and "
            + "cannot list windows; NO_IDE_RUNNING: no IDE with the plugin answers.")
    @McpOutputSchema(Windows.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult windows(
            @ToolParam(required = false, description = "Optional fully-qualified class name, e.g. "
                    + "com.example.OrderService. When given, each window is marked with whether it "
                    + "contains that class - the fastest way to tell two checkouts apart")
            String className) {

        IdeTargetStatus status = ideBridge.targetStatus(profileId);
        if (!status.selectable()) {
            return McpToolResult.of(new Windows(WindowsStatus.NOT_SELECTABLE, NOT_SELECTABLE, null, List.of(),
                    NextSteps.builder(advertised).followUp()));
        }

        IdeTargetsResult targets = ideBridge.discoverTargets(profileId, className);
        if (targets.instances().isEmpty()) {
            return McpToolResult.of(new Windows(WindowsStatus.NO_IDE_RUNNING, NO_IDE_RUNNING, null, List.of(),
                    NextSteps.builder(advertised).followUp()));
        }

        String recordingCommit = recordingCommit();
        boolean classGiven = className != null && !className.isBlank();
        List<Window> windows = new ArrayList<>();
        for (IdeInstanceView instance : targets.instances()) {
            for (IdeProjectView project : instance.projects()) {
                windows.add(new Window(
                        instance.ideName(),
                        project.name(),
                        project.id(),
                        project.vcsBranch(),
                        shortCommit(project.headCommit()),
                        commitAgreement(recordingCommit, project.headCommit()),
                        classGiven ? project.hasClass() : null,
                        project.focused(),
                        project.id().equals(targets.selectedProjectId()),
                        project.basePath()));
            }
        }
        List<Window> withClass = windows.stream().filter(window -> Boolean.TRUE.equals(window.hasClass())).toList();
        boolean linkedAlready = windows.stream().anyMatch(Window::linked);
        boolean onlyOne = withClass.size() == 1 && !linkedAlready;
        McpFollowUp followUp = NextSteps.builder(advertised)
                .nextWhen(onlyOne, () -> onProfile(IDE_LINK).with(PROJECT_ID, withClass.getFirst().projectId())
                        .why(LINK_WHY))
                .guidance(LINK_GUIDANCE)
                .guidance(recordingCommit == null
                        ? NO_COMMIT_GUIDANCE
                        : MOVED_ON_GUIDANCE.formatted(shortCommit(recordingCommit)))
                .followUp();
        return McpToolResult.of(new Windows(WindowsStatus.OK, null, shortCommit(recordingCommit),
                List.copyOf(windows), followUp));
    }

    @Tool(description = "Links one IntelliJ window to this profile, by the projectId of an ide_windows "
            + "window, so every later lookup resolves against that checkout. Needed only when several "
            + "windows could be meant: a single candidate is linked automatically by the first "
            + "lookup that needs it. A projectId no open window has is an error naming it.")
    @McpToolHints(readOnly = false, openWorld = true)
    @McpOutputSchema(Linked.class)
    @McpToolMeta(cost = McpToolCost.CHEAP, requires = McpToolRequirement.IDE_RUNNING)
    public McpToolResult link(
            @ToolParam(required = true, description = "The projectId of the window to link, copied "
                    + "from an ide_windows window")
            String projectId) {

        String wanted = ToolArguments.required(
                projectId, "projectId", "Call ide_windows for the projectId of each open window.");

        IdeTarget linked = findWindow(wanted).orElseThrow(() -> new IllegalArgumentException(
                "No open IntelliJ window has projectId " + wanted
                        + ". Call ide_windows for the windows that are actually open - a window "
                        + "that has since been closed is no longer listed."));
        ideBridge.selectTarget(profileId, linked);
        return McpToolResult.of(new Linked(linked.projectId(), linked.projectName(), linked.ideName(),
                linked.basePath(), NextSteps.builder(advertised).guidance(LINKED_GUIDANCE).followUp()));
    }

    @Tool(description = "Resolves where a class and method live in the reader's checkout - the absolute "
            + "file path and the line - through IntelliJ itself rather than from the frame name, so "
            + "nested classes, Kotlin facades, inherited methods and library code with sources "
            + "attached all resolve correctly. Does NOT move the reader's editor. The flamegraph and "
            + "trace exports carry call paths and numbers, not file paths, and the profiled build "
            + "may differ from the checkout. The answer says whether the file is decompiled (the "
            + "line numbers are a decompiler's), whether the position is imprecise (the class or "
            + "method declaration rather than the requested line) and whether the file has been "
            + "edited since the recording was taken. status NOT_LINKED: several windows could be "
            + "meant, listed as candidates; NO_IDE_RUNNING: no IDE with the plugin answers.")
    @McpOutputSchema(Location.class)
    @McpToolMeta(cost = McpToolCost.CHEAP, requires = McpToolRequirement.IDE_LINKED)
    public McpToolResult resolve(
            @ToolParam(required = true, description = "Fully-qualified class name exactly as the frame "
                    + "spells it, with $ for a nested class, e.g. com.example.OrderService$Batch")
            String className,
            @ToolParam(required = false, description = "Method name from the frame, without the class "
                    + "prefix and without a signature. Omit to locate the class itself")
            String methodName,
            @ToolParam(required = false, description = "Line number when one is known - the flamegraph "
                    + "export prints it after the method name (Class.method:214) when every sample at that "
                    + "frame agreed on one. Omit when unknown, and the answer is the method's declaration")
            Integer line) {

        String fqn = requireClassName(className);
        Optional<Unlinked> unlinked = ensureLinked(fqn);
        if (unlinked.isPresent()) {
            Unlinked answer = unlinked.get();
            return McpToolResult.of(new Location(answer.status(), answer.reason(), fqn, methodName, null, null, null,
                    false, false, false, null, answer.candidates(), answer.followUp()));
        }

        IdeResolveResult result = ideBridge.resolve(
                new IdeResolveRequest(profileId, fqn, methodName, lineOrUnknown(line), recordingTime()));
        if (!result.success()) {
            throw new ToolExecutionException(result.message());
        }

        return McpToolResult.of(new Location(
                LookupStatus.OK,
                null,
                fqn,
                methodName,
                result.file(),
                result.line(),
                result.kind(),
                result.decompiled(),
                result.imprecise(),
                result.stale(),
                epochMs(result.sourceMTime()),
                List.of(),
                resolvedFollowUp(fqn, result)));
    }

    @Tool(description = "Returns the source text of one class as the reader's IDE has it - for a "
            + "library class the attached sources, or a decompiled reconstruction when there are "
            + "none. For code outside the working directory, such as a dependency; for a file inside "
            + "the checkout, ide_resolve gives the path, which is cheaper to read directly.")
    @McpToolMeta(cost = McpToolCost.CHEAP, requires = McpToolRequirement.IDE_LINKED)
    public String source(
            @ToolParam(required = true, description = "Fully-qualified class name, e.g. "
                    + "com.example.OrderService")
            String className) {

        String fqn = requireClassName(className);
        Optional<Unlinked> unlinked = ensureLinked(fqn);
        if (unlinked.isPresent()) {
            return unlinked.get().text();
        }

        // No method: this asks for a class, and the field exists only for the single-URL bridge that
        // addresses source by `{fqn}.{method}`. It used to be required, so this passed the class name
        // twice to satisfy a field nothing here reads.
        IdeSourceResult result = ideBridge.fetchSource(new IdeSourceRequest(profileId, fqn, null));
        if (!result.success()) {
            return McpToolOutput.error(result.message());
        }

        String heading = result.decompiled()
                ? "Decompiled source of " + fqn + " - no sources are attached for it, so these line "
                        + "numbers are the decompiler's and do not match the original file."
                : "Source of " + fqn + ", as the IDE has it.";
        return McpToolOutput.capped(heading + System.lineSeparator() + System.lineSeparator()
                + result.content());
    }

    @Tool(description = "Opens a location in the reader's IntelliJ window and brings it to the front, "
            + "moving the cursor on their screen - for showing the reader something they asked to "
            + "see. ide_resolve answers the same question without touching their editor. status "
            + "NOT_LINKED: several windows could be meant, listed as candidates; NO_IDE_RUNNING: no "
            + "IDE with the plugin answers.")
    @McpToolHints(readOnly = false, openWorld = true)
    @McpOutputSchema(Opened.class)
    @McpToolMeta(cost = McpToolCost.CHEAP, requires = McpToolRequirement.IDE_LINKED)
    public McpToolResult open(
            @ToolParam(required = true, description = "Fully-qualified class name, e.g. "
                    + "com.example.OrderService")
            String className,
            @ToolParam(required = false, description = "Method name from the frame, without the class "
                    + "prefix and without a signature")
            String methodName,
            @ToolParam(required = false, description = "Line number when one is known; omit for the "
                    + "method's declaration")
            Integer line) {

        String fqn = requireClassName(className);
        Optional<Unlinked> unlinked = ensureLinked(fqn);
        if (unlinked.isPresent()) {
            Unlinked answer = unlinked.get();
            return McpToolResult.of(new Opened(answer.status(), answer.reason(), fqn, methodName, line,
                    answer.candidates(), answer.followUp()));
        }

        // The bridge requires a method for its open path; the class stands in when the caller has none,
        // which resolves to the class declaration rather than refusing over a missing frame detail.
        String method = methodName == null || methodName.isBlank() ? fqn : methodName;
        IdeOpenResult result = ideBridge.open(
                new IdeOpenRequest(profileId, fqn, method, lineOrUnknown(line)));
        if (!result.success()) {
            throw new ToolExecutionException(result.message());
        }
        return McpToolResult.of(new Opened(LookupStatus.OK, null, fqn, methodName, line, List.of(),
                NextSteps.builder(advertised).followUp()));
    }

    /**
     * Makes sure a window is linked, linking the only sensible candidate when there is one.
     *
     * @return empty when a window is linked and the lookup can proceed, otherwise the answer to give
     *         the caller instead — which names the candidates rather than asking them to guess
     */
    private Optional<Unlinked> ensureLinked(String fqn) {
        IdeTargetStatus status = ideBridge.targetStatus(profileId);
        if (status.linked()) {
            return Optional.empty();
        }
        if (!status.selectable()) {
            // The single-URL bridge is always "linked" in the sense that matters: there is one IDE.
            return Optional.empty();
        }

        IdeTargetsResult targets = ideBridge.discoverTargets(profileId, fqn);
        List<Candidate> candidates = candidates(targets);
        if (candidates.isEmpty()) {
            return Optional.of(new Unlinked(LookupStatus.NO_IDE_RUNNING, NO_IDE_RUNNING, List.of(),
                    NextSteps.builder(advertised).followUp()));
        }

        Optional<Candidate> only = onlyCandidate(candidates);
        if (only.isEmpty()) {
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .next(onProfile(IDE_WINDOWS).with(CLASS_NAME, fqn).why(WINDOWS_WHY))
                    .guidance(CHOOSE_GUIDANCE)
                    .followUp();
            return Optional.of(new Unlinked(LookupStatus.NOT_LINKED, NOT_LINKED,
                    candidates.stream().map(Candidate::window).toList(), followUp));
        }

        ideBridge.selectTarget(profileId, only.get().target());
        return Optional.empty();
    }

    /**
     * The one window this lookup can only have meant: the single one holding the class, or - when no
     * window admits to holding it, which is what happens for a frame in a dependency - the single
     * window there is. Anything else is a choice, and a choice belongs to the reader.
     */
    private static Optional<Candidate> onlyCandidate(List<Candidate> candidates) {
        List<Candidate> withClass = candidates.stream().filter(Candidate::hasClass).toList();
        if (withClass.size() == 1) {
            return Optional.of(withClass.getFirst());
        }
        if (withClass.isEmpty() && candidates.size() == 1) {
            return Optional.of(candidates.getFirst());
        }
        return Optional.empty();
    }

    private Optional<IdeTarget> findWindow(String projectId) {
        for (Candidate candidate : candidates(ideBridge.discoverTargets(profileId, null))) {
            if (projectId.equals(candidate.target().projectId())) {
                return Optional.of(candidate.target());
            }
        }
        return Optional.empty();
    }

    private static List<Candidate> candidates(IdeTargetsResult targets) {
        List<Candidate> candidates = new ArrayList<>();
        for (IdeInstanceView instance : targets.instances()) {
            for (IdeProjectView project : instance.projects()) {
                candidates.add(new Candidate(
                        new IdeTarget(
                                instance.port(),
                                project.id(),
                                instance.ideName(),
                                project.name(),
                                project.basePath(),
                                instance.pid()),
                        project.hasClass()));
            }
        }
        return candidates;
    }

    /**
     * What the reader should do with a location that came back qualified. Routing, not a verdict: it
     * says which fact about the file makes the line unsafe to cite, never that the finding is wrong.
     */
    private McpFollowUp resolvedFollowUp(String className, IdeResolveResult result) {
        NextSteps.Builder next = NextSteps.builder(advertised);
        if (result.decompiled()) {
            return next.next(onProfile(IDE_SOURCE).with(CLASS_NAME, className).why(SOURCE_WHY))
                    .guidance(DECOMPILED_GUIDANCE)
                    .followUp();
        }
        if (result.imprecise()) {
            return next.guidance(IMPRECISE_GUIDANCE).followUp();
        }
        if (result.stale()) {
            return next.next(onProfile(PROFILES_GET).why(COMMIT_WHY))
                    .guidance(STALE_GUIDANCE)
                    .followUp();
        }
        return next.guidance(READ_GUIDANCE).followUp();
    }

    private McpNextTool.Call onProfile(String tool) {
        return McpNextTool.call(tool).with(PROFILE_ID, profileId);
    }

    private String recordingCommit() {
        return recordingCommitResolver.resolve(profileManager.info().recordingId()).orElse(null);
    }

    /**
     * Compared as a prefix rather than for equality: a recording is routinely tagged with an
     * abbreviated commit while the IDE reports the full one, and treating those as different would
     * report every such profile as a mismatch.
     *
     * @return null when either commit is unknown
     */
    private static Boolean commitAgreement(String recordingCommit, String headCommit) {
        if (recordingCommit == null || headCommit == null) {
            return null;
        }
        String shorter = recordingCommit.length() <= headCommit.length() ? recordingCommit : headCommit;
        String longer = shorter.equals(recordingCommit) ? headCommit : recordingCommit;
        return longer.startsWith(shorter);
    }

    private static String shortCommit(String commit) {
        if (commit == null) {
            return null;
        }
        return commit.length() <= SHORT_COMMIT_LENGTH ? commit : commit.substring(0, SHORT_COMMIT_LENGTH);
    }

    /** The IDE's modification time, an ISO instant, as epoch milliseconds; null when absent or unreadable. */
    private static Long epochMs(String instant) {
        if (instant == null || instant.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(instant).toEpochMilli();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String requireClassName(String className) {
        return ToolArguments.required(
                className,
                "className",
                "It is the frame's fully-qualified class name, as flamegraph_export prints it.");
    }

    /**
     * The plugin's convention for "no line was reported", which is what it falls back to the method
     * declaration on. A zero or negative line from a caller means the same thing.
     */
    private static int lineOrUnknown(Integer line) {
        if (line == null || line < 1) {
            return -1;
        }
        return line;
    }

    private Instant recordingTime() {
        ProfileInfo info = profileManager.info();
        return info == null ? null : info.profilingStartedAt();
    }

    /** One window, with the one fact that decides whether it can be picked without asking. */
    private record Candidate(IdeTarget target, boolean hasClass) {

        CandidateWindow window() {
            return new CandidateWindow(target.projectId(), target.projectName(), target.ideName(), target.basePath(),
                    hasClass);
        }
    }

    /**
     * A lookup that could not proceed: no window is linked and none can be picked without asking.
     */
    private record Unlinked(LookupStatus status, String reason, List<CandidateWindow> candidates,
                            McpFollowUp followUp) {

        /** The same answer as text, for the one tool that answers with a document rather than a record. */
        String text() {
            StringBuilder answer = new StringBuilder(512).append(reason);
            if (!candidates.isEmpty()) {
                answer.append(System.lineSeparator()).append(System.lineSeparator());
                for (CandidateWindow candidate : candidates) {
                    answer.append("- ").append(candidate.project())
                            .append(" (projectId ").append(candidate.projectId()).append(")")
                            .append(candidate.hasClass() ? ", contains the class" : "")
                            .append(" at ").append(candidate.basePath())
                            .append(System.lineSeparator());
                }
                answer.append(System.lineSeparator()).append(CHOOSE_GUIDANCE);
            }
            return answer.toString();
        }
    }

    /** Whether the IDE windows could be read. */
    enum WindowsStatus {
        /** The windows are listed. */
        OK,
        /** The configured bridge serves a single IDE and cannot list or choose windows. */
        NOT_SELECTABLE,
        /** No IDE with the Jeffrey plugin answers on this machine. */
        NO_IDE_RUNNING
    }

    /** Whether a lookup could run against a window. */
    enum LookupStatus {
        /** It ran against the linked window. */
        OK,
        /** No window is linked and several could be meant; candidates lists them. */
        NOT_LINKED,
        /** No IDE with the Jeffrey plugin answers on this machine. */
        NO_IDE_RUNNING
    }

    record Window(
            String ide,
            String project,
            @McpDescription("Pass to ide_link")
            String projectId,
            @McpNullable
            String branch,
            @McpNullable
            @McpDescription("The first 12 characters of the commit the window's checkout is on")
            String headCommit,
            @McpNullable
            @McpDescription("Whether the checkout is on the commit the recording was tagged with; null when "
                    + "either commit is unknown")
            Boolean sameCommitAsRecording,
            @McpNullable
            @McpDescription("Whether the window contains the class asked about; null when none was given")
            Boolean hasClass,
            boolean focused,
            @McpDescription("Whether this profile is linked to the window")
            boolean linked,
            String basePath) {
    }

    record Windows(
            WindowsStatus status,
            @McpNullable
            String reason,
            @McpNullable
            @McpDescription("The first 12 characters of the commit the recording was tagged with; null when "
                    + "it carries none")
            String recordingCommit,
            List<Window> windows,
            McpFollowUp followUp) {
    }

    record Linked(String projectId, String project, String ide, String basePath, McpFollowUp followUp) {
    }

    record CandidateWindow(
            @McpDescription("Pass to ide_link")
            String projectId,
            String project,
            String ide,
            String basePath,
            boolean hasClass) {
    }

    /**
     * Where a class and method live in the checkout. The three caveats travel with every location:
     * the difference between a line a finding can cite and one it cannot is exactly those facts.
     */
    record Location(
            LookupStatus status,
            @McpNullable
            String reason,
            String className,
            @McpNullable
            String methodName,
            @McpNullable
            @McpDescription("The absolute path of the file; null unless status is OK")
            String file,
            @McpNullable
            Integer line,
            @McpNullable
            @McpDescription("How the IDE plugin found the position, as it names it: JAVA_LINE (a Java class, at "
                    + "the recorded line or its declaration), JAVA_PRECISE (a Java method found by name), "
                    + "KOTLIN_LINE (a Kotlin class) or KOTLIN_FALLBACK (a Kotlin file found by its facade's "
                    + "name); null unless status is OK")
            String kind,
            @McpDescription("The file is decompiled, so the line is the decompiler's")
            boolean decompiled,
            @McpDescription("The position is the declaration rather than the requested line")
            boolean imprecise,
            @McpDescription("The file was edited well after the recording was taken")
            boolean stale,
            @McpNullable
            @McpDescription("When the file was last modified, as UTC epoch milliseconds")
            Long sourceModifiedAtEpochMs,
            @McpDescription("The windows that could be meant, when status is NOT_LINKED")
            List<CandidateWindow> candidates,
            McpFollowUp followUp) {
    }

    record Opened(
            LookupStatus status,
            @McpNullable
            String reason,
            String className,
            @McpNullable
            String methodName,
            @McpNullable
            Integer line,
            @McpDescription("The windows that could be meant, when status is NOT_LINKED")
            List<CandidateWindow> candidates,
            McpFollowUp followUp) {
    }
}
