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

import cafe.jeffrey.microscope.core.manager.ide.IdeBridge;
import cafe.jeffrey.microscope.core.manager.ide.IdeOpenRequest;
import cafe.jeffrey.microscope.core.manager.ide.IdeOpenResult;
import cafe.jeffrey.microscope.core.manager.ide.IdeResolveRequest;
import cafe.jeffrey.microscope.core.manager.ide.IdeResolveResult;
import cafe.jeffrey.microscope.core.manager.ide.IdeSourceRequest;
import cafe.jeffrey.microscope.core.manager.ide.IdeSourceResult;
import cafe.jeffrey.microscope.core.manager.ide.IdeTarget;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetStatus;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetsResult;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetsResult.IdeInstanceView;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetsResult.IdeProjectView;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.McpToolHints;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Instant;
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
 * Jeffrey view, so there is nothing for {@code UiLinks} to point at.
 */
public class IdeMcpTools {

    private static final String NOT_LINKED_HEADING =
            "No IDE window is linked to this profile yet, and the right one could not be picked "
                    + "unambiguously.";

    private static final String NO_IDE_RUNNING =
            "No IntelliJ IDEA window with the Jeffrey plugin is answering on this machine. The plugin "
                    + "is installed from the JetBrains Marketplace as \"Jeffrey Microscope\" and can be "
                    + "switched off under Settings -> Tools -> Jeffrey Microscope Plugin; a disabled IDE "
                    + "is invisible here. Without one, map frames to code by reading the checkout "
                    + "directly.";

    private static final String NOT_SELECTABLE =
            "This installation is configured to use the third-party JFR Profiler plugin "
                    + "(jeffrey.microscope.ide.mode=jfr-profiler-plugin), which serves a single IDE and "
                    + "cannot list or choose windows. ide_source and ide_open still work; ide_windows "
                    + "and ide_link do not apply.";

    private static final String COMMIT_MATCHES = "yes";
    private static final String COMMIT_DIFFERS = "no";
    private static final String COMMIT_UNKNOWN = "";

    /** Enough of a commit to identify it in a table, and short enough not to dominate the row. */
    private static final int SHORT_COMMIT_LENGTH = 12;

    private final IdeBridge ideBridge;
    private final ProfileManager profileManager;
    private final RecordingCommitResolver recordingCommitResolver;
    private final String profileId;

    public IdeMcpTools(
            IdeBridge ideBridge,
            ProfileManager profileManager,
            RecordingCommitResolver recordingCommitResolver,
            String profileId) {

        this.ideBridge = ideBridge;
        this.profileManager = profileManager;
        this.recordingCommitResolver = recordingCommitResolver;
        this.profileId = profileId;
    }

    @Tool(description = "The IntelliJ windows open on this machine, which of them contains a given "
            + "class, and which one this profile is linked to. Also reports the branch and commit each "
            + "window is on against the commit the recording was tagged with, so a checkout that has "
            + "moved on from the profiled build is visible before any frame is mapped to a file. Call "
            + "this when ide_resolve reports that the window is ambiguous, or to check what the reader "
            + "actually has open.")
    public String windows(
            @ToolParam(required = false, description = "Optional fully-qualified class name, e.g. "
                    + "com.example.OrderService. When given, each window is marked with whether it "
                    + "contains that class - the fastest way to tell two checkouts apart")
            String className) {

        IdeTargetStatus status = ideBridge.targetStatus(profileId);
        if (!status.selectable()) {
            return NOT_SELECTABLE;
        }

        IdeTargetsResult targets = ideBridge.discoverTargets(profileId, className);
        if (targets.instances().isEmpty()) {
            return NO_IDE_RUNNING;
        }

        String recordingCommit = recordingCommit();
        MarkdownTable table = MarkdownTable.withColumns(
                "ide", "project", "project_id", "branch", "head_commit", "same_commit_as_recording",
                "has_class", "focused", "linked", "base_path");
        for (IdeInstanceView instance : targets.instances()) {
            for (IdeProjectView project : instance.projects()) {
                table.row(
                        instance.ideName(),
                        project.name(),
                        project.id(),
                        project.vcsBranch(),
                        shortCommit(project.headCommit()),
                        commitAgreement(recordingCommit, project.headCommit()),
                        className == null || className.isBlank() ? "" : project.hasClass(),
                        project.focused(),
                        project.id().equals(targets.selectedProjectId()),
                        project.basePath());
            }
        }

        return table
                .note(windowsNote(recordingCommit))
                .render();
    }

    @Tool(description = "Link one IntelliJ window to this profile, so every later lookup resolves "
            + "against that checkout. Takes a project_id from an ide_windows row. Only needed when "
            + "several windows are open and more than one could be meant: a single candidate is linked "
            + "automatically by the first lookup that needs it.")
    @McpToolHints(readOnly = false, openWorld = true)
    public String link(
            @ToolParam(required = true, description = "The project_id of the window to link, copied "
                    + "from an ide_windows row")
            String projectId) {

        String wanted = ToolArguments.required(
                projectId, "projectId", "Call ide_windows for the project_id of each open window.");

        Optional<IdeTarget> match = findWindow(wanted);
        if (match.isEmpty()) {
            return McpToolOutput.error(
                    "No open IntelliJ window has project_id " + wanted
                            + ". Call ide_windows for the windows that are actually open - a window "
                            + "that has since been closed is no longer listed.");
        }

        ideBridge.selectTarget(profileId, match.get());
        IdeTarget linked = match.get();
        return "Linked profile " + profileId + " to " + linked.projectName()
                + " in " + linked.ideName() + " (" + linked.basePath() + "). "
                + "ide_resolve, ide_source and ide_open now answer from that window.";
    }

    @Tool(description = "Where a class and method live in the reader's checkout: the absolute file "
            + "path and the line, resolved by IntelliJ itself rather than guessed from the frame name - "
            + "so nested classes, Kotlin facades, inherited methods and library code with sources "
            + "attached all resolve correctly. Does NOT move the reader's editor. Call it before "
            + "naming a file or a line in a finding: the flamegraph and trace exports carry call paths "
            + "and numbers, never source locations. The answer says whether the file is decompiled "
            + "(the line numbers are a decompiler's, not anybody's), whether the position is imprecise "
            + "(the class or method declaration rather than the requested line) and whether the file "
            + "has been edited since the recording was taken.")
    public String resolve(
            @ToolParam(required = true, description = "Fully-qualified class name exactly as the frame "
                    + "spells it, with $ for a nested class, e.g. com.example.OrderService$Batch")
            String className,
            @ToolParam(required = false, description = "Method name from the frame, without the class "
                    + "prefix and without a signature. Omit to locate the class itself")
            String methodName,
            @ToolParam(required = false, description = "Line number when one is known - the flamegraph "
                    + "export prints it after the method name. Omit when unknown, and the answer is the "
                    + "method's declaration")
            Integer line) {

        String fqn = requireClassName(className);
        Optional<String> unlinked = ensureLinked(fqn);
        if (unlinked.isPresent()) {
            return unlinked.get();
        }

        IdeResolveResult result = ideBridge.resolve(
                new IdeResolveRequest(profileId, fqn, methodName, lineOrUnknown(line), recordingTime()));
        if (!result.success()) {
            return McpToolOutput.error(result.message());
        }

        return McpToolOutput.json(new ResolvedLocation(
                result.file(),
                result.line(),
                result.kind(),
                result.decompiled(),
                result.imprecise(),
                result.stale(),
                result.sourceMTime(),
                nextStepFor(result)));
    }

    @Tool(description = "The source text of one class, as the reader's IDE has it - including library "
            + "classes, where it returns the attached sources when they are there and a decompiled "
            + "reconstruction when they are not. Use it for code the agent cannot open itself, such as "
            + "a dependency outside the working directory; for a file inside the checkout, ide_resolve "
            + "gives the path and reading it directly is cheaper.")
    public String source(
            @ToolParam(required = true, description = "Fully-qualified class name, e.g. "
                    + "com.example.OrderService")
            String className) {

        String fqn = requireClassName(className);
        Optional<String> unlinked = ensureLinked(fqn);
        if (unlinked.isPresent()) {
            return unlinked.get();
        }

        // The class stands in for the method: the request carries one because the single-URL bridge
        // rebuilds a `{fqn}.{method}` path from it and drops the last segment again at the other end.
        // Passing the class name round-trips to the same class, and there is no method to name here.
        IdeSourceResult result = ideBridge.fetchSource(new IdeSourceRequest(profileId, fqn, fqn));
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

    @Tool(description = "Open a location in the reader's IntelliJ window and bring it to the front. "
            + "This moves the cursor on somebody's screen, so call it when the reader has asked to be "
            + "shown something, not while gathering evidence - ide_resolve answers the same question "
            + "without touching their editor.")
    @McpToolHints(readOnly = false, openWorld = true)
    public String open(
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
        Optional<String> unlinked = ensureLinked(fqn);
        if (unlinked.isPresent()) {
            return unlinked.get();
        }

        // The bridge requires a method for its open path; the class stands in when the caller has none,
        // which resolves to the class declaration rather than refusing over a missing frame detail.
        String method = methodName == null || methodName.isBlank() ? fqn : methodName;
        IdeOpenResult result = ideBridge.open(
                new IdeOpenRequest(profileId, fqn, method, lineOrUnknown(line)));
        if (!result.success()) {
            return McpToolOutput.error(result.message());
        }
        return "Opened " + fqn + (methodName == null || methodName.isBlank() ? "" : "." + methodName)
                + " in the reader's IDE.";
    }

    /**
     * Makes sure a window is linked, linking the only sensible candidate when there is one.
     *
     * @return empty when a window is linked and the lookup can proceed, otherwise the answer to give
     *         the caller instead — which names the candidates rather than asking them to guess
     */
    private Optional<String> ensureLinked(String fqn) {
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
            return Optional.of(NO_IDE_RUNNING);
        }

        Optional<Candidate> only = onlyCandidate(candidates);
        if (only.isEmpty()) {
            return Optional.of(ambiguous(candidates));
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

    private String ambiguous(List<Candidate> candidates) {
        StringBuilder answer = new StringBuilder(512).append(NOT_LINKED_HEADING);
        answer.append(System.lineSeparator()).append(System.lineSeparator());
        for (Candidate candidate : candidates) {
            answer.append("- ").append(candidate.target().projectName())
                    .append(" (project_id ").append(candidate.target().projectId()).append(")")
                    .append(candidate.hasClass() ? ", contains the class" : "")
                    .append(" at ").append(candidate.target().basePath())
                    .append(System.lineSeparator());
        }
        answer.append(System.lineSeparator())
                .append("Call ide_link with the project_id of the one that matches this profile, then "
                        + "repeat the lookup. ide_windows shows the branch and commit of each, which is "
                        + "the surest way to tell them apart.");
        return answer.toString();
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
    private static String nextStepFor(IdeResolveResult result) {
        if (result.decompiled()) {
            return "This file is decompiled - the line is the decompiler's, not the library's source. "
                    + "Cite the method, not the line, and use ide_source to read it.";
        }
        if (result.imprecise()) {
            return "The position is the declaration rather than the requested line: read the method "
                    + "and locate the statement before citing a line.";
        }
        if (result.stale()) {
            return "The file has been edited well after the recording was taken, so this line may "
                    + "describe code that no longer exists. Read it before citing it, and check "
                    + "profiles_get.recordingCommit against the checkout.";
        }
        return "Read the file at this line before describing what it does.";
    }

    private String windowsNote(String recordingCommit) {
        StringBuilder note = new StringBuilder(384)
                .append("Pass a project_id to ide_link to bind one of these windows to this profile. ")
                .append("An empty `has_class` means no class name was given, not that the class is ")
                .append("missing.");
        if (recordingCommit == null) {
            note.append(" The recording carries no commit tag, so `same_commit_as_recording` is blank ")
                    .append("throughout and no window can be confirmed as the profiled build.");
        } else {
            note.append(" The recording was built from commit ").append(shortCommit(recordingCommit))
                    .append("; a window marked `no` has moved on from it, and frames may not match ")
                    .append("what is on disk there.");
        }
        return note.toString();
    }

    private String recordingCommit() {
        return recordingCommitResolver.resolve(profileManager.info().recordingId()).orElse(null);
    }

    /**
     * Compared as a prefix rather than for equality: a recording is routinely tagged with an
     * abbreviated commit while the IDE reports the full one, and treating those as different would
     * report every such profile as a mismatch.
     */
    private static String commitAgreement(String recordingCommit, String headCommit) {
        if (recordingCommit == null || headCommit == null) {
            return COMMIT_UNKNOWN;
        }
        String shorter = recordingCommit.length() <= headCommit.length() ? recordingCommit : headCommit;
        String longer = shorter.equals(recordingCommit) ? headCommit : recordingCommit;
        return longer.startsWith(shorter) ? COMMIT_MATCHES : COMMIT_DIFFERS;
    }

    private static String shortCommit(String commit) {
        if (commit == null) {
            return "";
        }
        return commit.length() <= SHORT_COMMIT_LENGTH ? commit : commit.substring(0, SHORT_COMMIT_LENGTH);
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
    }

    /**
     * @param nextStep what to do with this location, given how it resolved. Present on every answer:
     *                 the flags alone leave a reader to work out which of them matters
     */
    private record ResolvedLocation(
            String file,
            Integer line,
            String kind,
            boolean decompiled,
            boolean imprecise,
            boolean stale,
            String sourceMTime,
            String nextStep) {
    }
}
