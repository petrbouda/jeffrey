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

package cafe.jeffrey.ide.plugin.idea.recording;

import cafe.jeffrey.ide.plugin.idea.agent.AgentCli;
import cafe.jeffrey.ide.plugin.idea.agent.AgentLaunchers;
import cafe.jeffrey.ide.plugin.idea.agent.AgentTask;
import cafe.jeffrey.ide.plugin.idea.recording.web.CefPanelRenderer;
import cafe.jeffrey.ide.plugin.idea.settings.JeffreySettings;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.concurrency.AppExecutorUtil;

import javax.swing.JComponent;
import java.awt.BorderLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The recording panel: what Microscope knows about this file, and what it can open.
 *
 * <p>Five states, and the panel only ever shows one — never analysed, being analysed, ready,
 * Microscope not reachable, or an analysis that threw. They are drawn rather than merely logged
 * because the point of the tab is to answer "what happens if I press this" before it is pressed.
 *
 * <p><b>This class draws nothing.</b> It owns the conversation with Microscope, the state machine
 * over it, and the actions the page can trigger; a {@link PanelRenderer} turns that into pixels. Two
 * exist — the IDE's bundled Chromium where it runs, Swing's HTML kit where it does not — and which
 * one is in use is decided once, here, at construction.
 *
 * <p><b>What it deliberately does not draw.</b> No flame graph, no chart, no hot-method list. The
 * ready state shows four figures and the auto-analysis lines, which is the narrow exception the
 * plugin's rule allows; everything past that is a link into Microscope, because a second renderer
 * here is a second place for the two to disagree about what a recording says.
 */
public final class RecordingPanel extends JBPanel<RecordingPanel> implements PanelActions, Disposable {

    private static final Logger LOG = Logger.getInstance(RecordingPanel.class);

    private static final String SETTINGS_DISPLAY_NAME = "Jeffrey Plugin";

    /** How often the panel asks about a running pipeline. Stages take seconds to minutes. */
    private static final long BUILD_POLL_SECONDS = 2;

    /**
     * How many "nothing running" answers in a row end the watch after a build was requested. The
     * run is registered asynchronously, so the first poll can land before it exists; three in a row
     * mean it never will.
     */
    private static final int BUILD_IDLE_POLLS_TOLERATED = 3;

    /**
     * How many polls in a row may fail before the watch gives up. One missed answer is a hiccup;
     * five is a Microscope that stopped, or an endpoint that is not there, and a panel that keeps
     * asking every two seconds forever helps nobody.
     */
    private static final int BUILD_FAILED_POLLS_TOLERATED = 5;

    private final Project project;
    private final Path file;
    private final PanelRenderer renderer;

    /** One watch at a time: a second "Build index" while one is running must not start a second loop. */
    private final AtomicBoolean watchingBuild = new AtomicBoolean();

    /** One candidate scan at a time: the menu is rebuilt on every render and must not refetch each time. */
    private final AtomicBoolean scanningCandidates = new AtomicBoolean();

    /**
     * Whether the candidate menu needs rebuilding. Set by every {@link #refresh()} — reselecting the
     * tab or pressing Retry is the gesture that means "ask again" — so a recording analysed since
     * the last scan stops being offered as one that would import first, and a scan that ran while
     * Microscope was down is not cached as an empty menu for the life of the tab.
     */
    private volatile boolean candidatesStale = true;

    private volatile MicroscopeClient client;
    private volatile boolean disposed;

    /**
     * The file this recording is measured against, and what Microscope holds for it.
     *
     * <p>Both live with the tab rather than in any store: closing the panel ends the comparison, the
     * same lifetime Microscope gives the baseline picked in its own UI. A comparison is a question
     * being asked right now, not a property of the file.
     */
    private volatile Path baselineFile;
    private volatile RecordingState baseline;

    /**
     * The baseline whose import is running, so a second pick of the same file does not start a
     * second import of it. Reachable without patience: the menu is hidden while a baseline is
     * attached, so re-picking one means clearing first, and a clear during a long import leaves that
     * import running.
     */
    private final AtomicReference<Path> importingBaseline = new AtomicReference<>();

    private volatile List<CompareCandidate> candidates = List.of();

    public RecordingPanel(Project project, Path file) {
        super(new BorderLayout());
        this.project = project;
        this.file = file;
        this.client = new MicroscopeClient(JeffreySettings.getInstance().microscopeUrl());
        this.renderer = createRenderer();

        add(renderer.component(), BorderLayout.CENTER);

        // The stylesheet is built from the current theme by both renderers, so a theme switch has to
        // be a re-render. There was no listener for this before, and the panel only re-themed by
        // accident when the tab was reselected.
        ApplicationManager.getApplication().getMessageBus().connect(this)
                .subscribe(LafManagerListener.TOPIC, (LafManagerListener) source -> renderer.themeChanged());

        renderer.showLoading();
        query();
    }

    /**
     * Chromium where the runtime has it, Swing's HTML kit otherwise.
     *
     * <p>{@code isSupported()} is false on a JBR built without JCEF and inside the JetBrains Client,
     * so the fallback is not theoretical. Anything thrown while building the browser lands here too:
     * a tab that renders plainly beats a tab that renders an exception.
     *
     * <p>The guard wraps the {@code isSupported()} call as well, not only the constructor. The JCEF
     * classes are an optional dependency (a plugin of their own since 2026.2), and when they are
     * absent the first mention of {@link CefPanelRenderer} fails while that class is being linked
     * — before any line of it runs. That {@link LinkageError} has to be caught here, one frame up.
     */
    private PanelRenderer createRenderer() {
        try {
            if (CefPanelRenderer.isSupported()) {
                CefPanelRenderer cef = new CefPanelRenderer(this, file);
                Disposer.register(this, cef);
                return cef;
            }
            LOG.info("JCEF is unavailable in this runtime, rendering the recording panel with Swing");
        } catch (LinkageError e) {
            LOG.info("JCEF classes are not present in this IDE, rendering the recording panel with Swing: "
                    + e);
        } catch (Exception e) {
            LOG.warn("Could not start the embedded browser, falling back to the Swing panel", e);
        }
        return new SwingPanelRenderer(this, file);
    }

    /** The component the tab should focus — the renderer's, not this wrapper. */
    public JComponent focusComponent() {
        return renderer.component();
    }

    /**
     * Re-reads the Microscope address, so a corrected URL takes effect without reopening the tab.
     *
     * <p>The baseline survives: a comparison lives with the tab, and reselecting the tab is not a
     * reason to end one. A candidate scan that found nothing is allowed to run again, because the
     * commonest reason for an empty menu is that Microscope was not answering when it ran; one that
     * found recordings is kept, so switching tabs does not re-ask about every file each time.
     */
    public void refresh() {
        MicroscopeClient previous = client;
        client = new MicroscopeClient(JeffreySettings.getInstance().microscopeUrl());
        closeLater(previous);
        candidatesStale = true;
        renderer.showLoading();
        query();
    }

    /**
     * Releases a replaced client off the EDT.
     *
     * <p>{@code HttpClient.close()} waits for every request still in flight, and this runs on the
     * EDT: {@code selectNotify} calls {@link #refresh()} whenever the tab is brought forward. With a
     * baseline importing — an hour's timeout, minutes in practice — reselecting the tab would freeze
     * the IDE until the import returned. Nothing waits on the old client, so the wait belongs on a
     * pooled thread.
     */
    private static void closeLater(MicroscopeClient previous) {
        AppExecutorUtil.getAppExecutorService().execute(previous::close);
    }

    @Override
    public void dispose() {
        disposed = true;
        closeLater(client);
    }

    /**
     * Asks Microscope about the file and draws the answer. A file with something running gets one
     * more question — how far has it got? — so a tab opened while Microscope is working shows the
     * progress rather than a bar that never moves, or an offer to start a second build.
     * <p>
     * Two pipelines can be running, never both at once: a profile is either still being built out of
     * the recording, or ready with its dump not yet indexed.
     */
    private void query() {
        MicroscopeClient current = client;
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            RecordingState state = current.state(file);
            PipelineBuild.Pipeline pipeline = runningPipeline(state);
            if (pipeline != null) {
                PipelineBuild build = progressOrNull(current, pipeline, state.profileId());
                if (build != null && !build.failed()) {
                    RecordingState watched = state.withBuild(build);
                    show(watched);
                    watchBuild(current, watched, pipeline);
                    return;
                }
            }
            // Drawn once. A ready profile carries its findings already: Microscope runs the rule set
            // alongside the parse and the import waits for it, so there is no gap here to poll for.
            show(state);
        });
    }

    /**
     * Which pipeline this state could be waiting on, or null when it is waiting on nothing.
     */
    private static PipelineBuild.Pipeline runningPipeline(RecordingState state) {
        if (state.needsHeapIndex()) {
            return PipelineBuild.Pipeline.HEAP_INDEX;
        }
        if (state.isAnalysisFollowable()) {
            return PipelineBuild.Pipeline.PROFILE_INIT;
        }
        return null;
    }

    // --- actions the page can trigger -----------------------------------------------------------

    @Override
    public void analyze() {
        MicroscopeClient current = client;
        renderer.render(new PanelState(
                new RecordingState(
                        RecordingState.Status.ANALYZING, null, null, file.getFileName().toString(), 0L, null),
                baseline,
                candidates));

        AppExecutorUtil.getAppExecutorService().execute(() -> {
            try {
                current.analyze(file);
                query();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOG.warn("Analyzing a recording in Microscope failed: file=" + file, e);
                ApplicationManager.getApplication().invokeLater(() -> renderer.showFailure(message(e)));
            }
        });
    }

    @Override
    public void retry() {
        refresh();
    }

    @Override
    public void checkAgain() {
        query();
    }

    @Override
    public void openSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(null, SETTINGS_DISPLAY_NAME);
    }

    /**
     * Starts the index build and follows it. The panel redraws on every poll — the callout is what
     * moves, the rest of the page stays — and drops back to an ordinary ready state, figures and all,
     * once the pipeline is no longer running.
     */
    @Override
    public void buildIndex() {
        MicroscopeClient current = client;
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            RecordingState state = current.state(file);
            if (!state.needsHeapIndex()) {
                LOG.info("Ignoring an index build for a file that does not need one: file=" + file);
                show(state);
                return;
            }
            try {
                current.buildHeapIndex(state.profileId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOG.warn("Starting a heap index build in Microscope failed: file=" + file, e);
                show(state.withBuild(new PipelineBuild(
                        PipelineBuild.Pipeline.HEAP_INDEX,
                        PipelineBuild.Phase.FAILED, 0, 0, null, 0L, message(e))));
                return;
            }
            show(state.withBuild(new PipelineBuild(
                    PipelineBuild.Pipeline.HEAP_INDEX,
                    PipelineBuild.Phase.RUNNING, 0, 0, null, 0L, null)));
            watchBuild(current, state, PipelineBuild.Pipeline.HEAP_INDEX);
        });
    }

    /**
     * Polls the build until it stops, one scheduled poll at a time rather than a thread that sleeps.
     * A closed tab ends the watch at the next poll; the build itself carries on in Microscope.
     */
    private void watchBuild(MicroscopeClient current, RecordingState state, PipelineBuild.Pipeline pipeline) {
        if (!watchingBuild.compareAndSet(false, true)) {
            return;
        }
        pollBuild(current, state, pipeline, 0, 0);
    }

    private void pollBuild(
            MicroscopeClient current,
            RecordingState state,
            PipelineBuild.Pipeline pipeline,
            int idlePolls,
            int failedPolls) {

        if (disposed) {
            watchingBuild.set(false);
            return;
        }
        AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
            PipelineBuild build;
            try {
                build = readProgress(current, pipeline, state.profileId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                watchingBuild.set(false);
                return;
            } catch (Exception e) {
                // One missed poll is not a failed build; the next one will say. Keep watching, up
                // to a point — then stop and draw whatever Microscope will answer about the file.
                if (failedPolls + 1 < BUILD_FAILED_POLLS_TOLERATED) {
                    LOG.info("Could not read the pipeline progress, will ask again: file=" + file
                            + " pipeline=" + pipeline, e);
                    pollBuild(current, state, pipeline, idlePolls, failedPolls + 1);
                    return;
                }
                LOG.warn("Gave up following the pipeline: file=" + file + " pipeline=" + pipeline, e);
                watchingBuild.set(false);
                query();
                return;
            }

            if (build == null) {
                // Idle or completed. Right after the request the run may not exist yet, so a few of
                // these are patience; more than that and there is nothing to wait for.
                if (idlePolls + 1 < BUILD_IDLE_POLLS_TOLERATED && state.build() == null) {
                    pollBuild(current, state, pipeline, idlePolls + 1, 0);
                    return;
                }
                watchingBuild.set(false);
                query();
                return;
            }
            RecordingState watched = state.withBuild(build);
            show(watched);
            if (build.failed()) {
                watchingBuild.set(false);
                return;
            }
            pollBuild(current, watched, pipeline, 0, 0);
        }, BUILD_POLL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Both pipelines answer the same shape from endpoints a segment apart, and this is the only place
     * that has to know which one it is asking.
     */
    private static PipelineBuild readProgress(
            MicroscopeClient current, PipelineBuild.Pipeline pipeline, String profileId)
            throws IOException, InterruptedException {

        return switch (pipeline) {
            case HEAP_INDEX -> current.heapIndexProgress(profileId);
            case PROFILE_INIT -> current.profileInitProgress(profileId);
        };
    }

    private static PipelineBuild progressOrNull(
            MicroscopeClient current, PipelineBuild.Pipeline pipeline, String profileId) {
        try {
            return readProgress(current, pipeline, profileId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Draws a recording, together with whatever comparison the tab currently holds.
     *
     * <p>The baseline is read here rather than passed in, so every path that redraws the panel — a
     * poll, a retry, an import that just finished — keeps the comparison without having to know it
     * exists.
     */
    private void show(RecordingState state) {
        if (disposed) {
            return;
        }
        PanelState panel = new PanelState(state, baseline, candidates);
        ApplicationManager.getApplication().invokeLater(() -> renderer.render(panel));
        if (state.status() == RecordingState.Status.READY && !state.isHeapDumpFile()) {
            scanCandidates();
        }
    }

    /**
     * Looks up every other recording in the project, once per panel, and redraws when they arrive.
     *
     * <p>Each is asked of Microscope by the same by-path call the panel makes about its own file, so
     * the menu can say which of them can be compared today and how long each one ran. That is one
     * request per recording against a server on this machine, done once and kept — the alternative,
     * a menu of bare file names, is what lets a reader pick a four-minute baseline for a twenty-minute
     * primary and believe the result.
     */
    private void scanCandidates() {
        if (!candidatesStale || !scanningCandidates.compareAndSet(false, true)) {
            return;
        }
        // Cleared before the work rather than after it, so a refresh arriving mid-scan marks the
        // result stale and the next render asks again — the two scans are still serialised by the
        // flag above, so the later answer is always the one that stands.
        candidatesStale = false;

        MicroscopeClient current = client;
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            try {
                List<Path> files = RecordingsInProject.find(project, file);
                List<CompareCandidate> found = new ArrayList<>(files.size());
                for (Path candidate : files) {
                    found.add(new CompareCandidate(candidate, current.state(candidate)));
                }
                candidates = List.copyOf(found);
            } catch (ProcessCanceledException e) {
                // The platform cancelling us is not a failure to report; it is an instruction.
                candidatesStale = true;
                throw e;
            } catch (Exception e) {
                // A menu that cannot be filled is not worth failing a panel over: the comparison can
                // still be started from the Project view, which needs no scan at all. Left stale, so
                // the next refresh tries again rather than keeping an empty menu for the tab's life.
                candidatesStale = true;
                LOG.info("Could not list the recordings in this project: file=" + file, e);
                return;
            } finally {
                scanningCandidates.set(false);
            }
            if (!candidates.isEmpty()) {
                query();
            }
        });
    }

    /**
     * Opens the profile where its kind says it should open — a recording on the dashboard, a dump on
     * its overview. The bare profile URL is not used: Microscope redirects it to the JFR dashboard
     * whatever the profile holds, and a heap dump would land on a summary of events it has none of.
     */
    @Override
    public void openProfile() {
        withProfile(state -> {
            RecordingState.ProfileSummary summary = state.summary();
            if (summary == null) {
                BrowserUtil.browse(client.profileUrl(state.profileId()));
                return;
            }
            // While comparing, the button lands on the comparison rather than on the primary's own
            // dashboard: the pair is what this tab is about, and the dashboard says nothing about it.
            // A pair the verdict calls incomparable keeps the primary's landing page, because the
            // differential one cannot draw it and the panel has already said so.
            String landing = opensComparison(state)
                    ? ProfileView.DIFFERENTIAL.getFirst().path()
                    : summary.landingPath();
            BrowserUtil.browse(viewUrl(state, landing));
        });
    }

    @Override
    public void openView(String viewPath) {
        if (viewPath == null || viewPath.isBlank()) {
            return;
        }
        withProfile(state -> BrowserUtil.browse(viewUrl(state, viewPath)));
    }

    /**
     * A link into Microscope, carrying the baseline when there is one.
     *
     * <p>Microscope's differential pages read their baseline from session storage, which its own
     * picker writes; without the parameter a link could open the primary and nothing else, and the
     * comparison set up here would have to be set up again on the other side.
     */
    private String viewUrl(RecordingState recording, String viewPath) {
        String baselineProfileId = opensComparison(recording) ? comparedProfileId() : null;
        return client.viewUrl(recording.profileId(), viewPath, baselineProfileId);
    }

    /**
     * Whether a link may point at the differential pages: a pair exists and can be drawn.
     *
     * <p>Takes the recording the caller already resolved rather than asking Microscope again — every
     * link would otherwise cost an extra round trip to answer a question about a state in hand.
     */
    private boolean opensComparison(RecordingState recording) {
        return new PanelState(recording, baseline, candidates).opensComparison();
    }

    /**
     * The baseline's profile id while a comparison can actually be opened, or null otherwise — a
     * baseline still importing has no profile to name, and naming it would produce a link that
     * lands on an error.
     */
    private String comparedProfileId() {
        RecordingState current = baseline;
        if (current == null || current.status() != RecordingState.Status.READY) {
            return null;
        }
        return current.profileId();
    }

    /**
     * Hands the agent the profile id, never the file path — neither Claude Code nor Codex can parse a
     * JFR, and Microscope has already done it. The prompt carries no question of its own: the method
     * lives in the agent's {@code analyze-jfr} skill, and the panel does not know what the developer
     * wants to ask.
     *
     * <p>Launching also remembers the agent, which is what the split button's primary half runs next
     * time. Without it that choice would fall to whichever entry {@code AgentCli.ALL} declares first.
     */
    @Override
    public void launchAgent(AgentCli agent) {
        JeffreySettings.getInstance().setPreferredAgent(agent.executable());

        AppExecutorUtil.getAppExecutorService().execute(() -> {
            RecordingState state = client.state(file);
            if (state.profileId() == null) {
                LOG.info("Ignoring an agent launch for a recording with no profile: file=" + file);
                return;
            }
            String command = agent.command(taskFor(state));
            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    AgentLaunchers.current().launch(project, workingDirectory(), command);
                } catch (Exception e) {
                    LOG.warn("Could not start an agent: agent=" + agent.executable() + " file=" + file, e);
                }
            });
        });
    }

    /**
     * Attaches a baseline, importing it into Microscope first when it has never seen it.
     *
     * <p>The file this tab was opened on stays the primary. That is the whole of the direction, and
     * it is decided here rather than anywhere downstream: read the other way round, every regression
     * in the comparison would report as an improvement.
     *
     * <p>An import runs for as long as an import runs — minutes for a large recording — and the strip
     * says so meanwhile rather than the panel appearing to have ignored the click.
     */
    @Override
    public void compareWith(Path picked) {
        if (picked == null || picked.equals(file)) {
            LOG.info("Ignoring a comparison of a recording with itself: file=" + file);
            return;
        }
        MicroscopeClient current = client;
        baselineFile = picked;

        AppExecutorUtil.getAppExecutorService().execute(() -> {
            RecordingState state = current.state(picked);
            if (!adoptBaseline(picked, state)) {
                return;
            }
            if (state.status() != RecordingState.Status.NOT_IMPORTED
                    && state.status() != RecordingState.Status.IMPORTED) {
                return;
            }
            // An import of this exact file is already running — the developer cleared the comparison
            // and picked the same baseline again while waiting. Let the one in flight finish and
            // adopt: a second import would build a second profile of identical bytes, and whichever
            // failed would leave the strip claiming Microscope cannot describe a file it just read.
            if (picked.equals(importingBaseline.getAndSet(picked))) {
                return;
            }
            try {
                current.analyze(picked);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                LOG.warn("Could not analyse a baseline recording: file=" + picked, e);
                adoptBaseline(picked, RecordingState.unavailable(
                        picked.getFileName().toString(), state.sizeInBytes()));
                return;
            } finally {
                importingBaseline.compareAndSet(picked, null);
            }
            adoptBaseline(picked, current.state(picked));
        });
    }

    /**
     * Records what Microscope says about the baseline and redraws, unless the developer has since
     * picked another one or cleared the comparison — an import takes minutes, and the answer to a
     * question nobody is asking any more must not overwrite the one they are.
     */
    private boolean adoptBaseline(Path picked, RecordingState state) {
        if (!picked.equals(baselineFile)) {
            return false;
        }
        baseline = state;
        query();
        return true;
    }

    /**
     * Exchanges the two files by opening the baseline's own tab with this recording attached to it.
     *
     * <p>Reopening rather than flipping a field, because the primary is the file whose tab this is —
     * its name in the title, its figures in the header, its profile behind every link. A swap that
     * left those alone would only relabel the strip and quietly change what "more" means.
     */
    @Override
    public void swapComparison() {
        Path newPrimary = baselineFile;
        if (newPrimary == null) {
            return;
        }
        VirtualFile target = LocalFileSystem.getInstance().findFileByNioFile(newPrimary);
        if (target == null) {
            LOG.info("Cannot swap to a baseline that is no longer on disk: file=" + newPrimary);
            return;
        }
        Path newBaseline = file;
        ApplicationManager.getApplication().invokeLater(() -> {
            RecordingPanel opened = RecordingPanels.open(project, target);
            if (opened == null) {
                // The platform gave the file to another editor — .hprof is shared with IntelliJ's own
                // viewer. Keep this tab's comparison: dropping it first would lose the pair from both
                // sides on one click.
                LOG.info("The swapped-to recording did not open in a Microscope panel: file=" + newPrimary);
                return;
            }
            opened.compareWith(newBaseline);
            clearComparison();
        });
    }

    @Override
    public void clearComparison() {
        baselineFile = null;
        baseline = null;
        query();
    }

    /**
     * Resolves the profile the panel last saw, rather than one captured when the document was drawn,
     * so a stale page cannot outlive the profile it described.
     */
    private void withProfile(Consumer<RecordingState> onProfile) {
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            RecordingState state = client.state(file);
            if (state.profileId() == null) {
                LOG.info("Ignoring a view link for a recording with no profile: file=" + file);
                return;
            }
            onProfile.accept(state);
        });
    }

    /**
     * What to ask the agent, which is decided by what the panel is showing.
     *
     * <p>A comparison asks {@code compare-jfr}, a dump asks {@code analyze-heap} and a recording asks
     * {@code analyze-jfr}: three skills, and the wording is the only thing that picks between them.
     */
    private AgentTask taskFor(RecordingState state) {
        String baselineProfileId = comparedProfileId();
        if (baselineProfileId != null) {
            return new AgentTask.Compare(state.profileId(), baselineProfileId);
        }
        if (state.summary() != null && state.summary().isHeapDump()) {
            return new AgentTask.AnalyseHeapDump(state.profileId());
        }
        return new AgentTask.AnalyseRecording(state.profileId());
    }

    /** The project root, so the agent starts where the developer's code is, not beside the recording. */
    private Path workingDirectory() {
        String basePath = project.getBasePath();
        if (basePath != null) {
            return Path.of(basePath);
        }
        Path parent = file.getParent();
        return parent == null ? file : parent;
    }

    private static String message(Exception cause) {
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
