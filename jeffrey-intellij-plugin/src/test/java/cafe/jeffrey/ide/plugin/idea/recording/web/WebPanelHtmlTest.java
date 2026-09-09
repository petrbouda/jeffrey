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

package cafe.jeffrey.ide.plugin.idea.recording.web;

import cafe.jeffrey.ide.plugin.idea.agent.AgentCli;
import cafe.jeffrey.ide.plugin.idea.agent.AgentRow;
import cafe.jeffrey.ide.plugin.idea.recording.PipelineBuild;
import cafe.jeffrey.ide.plugin.idea.recording.ProfileView;
import cafe.jeffrey.ide.plugin.idea.recording.CompareCandidate;
import cafe.jeffrey.ide.plugin.idea.recording.PanelState;
import cafe.jeffrey.ide.plugin.idea.recording.RecordingState;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The web document the panel renders.
 *
 * <p>Worth testing directly for the same reason the Swing markup is: every mistake here is silent. A
 * tile that stays clickable when the feature is gated, a heap dump that grows an empty findings
 * section, an apostrophe in a file name closing an attribute early — none of it throws, and all of it
 * reaches the developer as a wrong panel.
 *
 * <p>Assertions are on {@code data-action} and {@code data-view} rather than on class names, because
 * those attributes are the contract with {@link CefPanelRenderer}: a rename there breaks a click, a
 * rename of a CSS class breaks nothing but a colour.
 */
public class WebPanelHtmlTest {

    private static final Path FILE = Path.of("/home/dev/jeffrey/jeffrey-20260904-180108.jfr");
    private static final String URL = "http://localhost:8585";

    private static final AgentCli CLAUDE = new AgentCli("Claude", "claude");
    private static final AgentCli CODEX = new AgentCli("Codex", "codex");

    /** Most assertions are about a panel with no comparison; the pair has its own tests below. */
    private static PanelState panel(RecordingState state) {
        return PanelState.of(state);
    }

    @Test
    public void readyCarriesTheFourFigures() {
        String html = document(ready(List.of(), List.of()));

        assertTrue(html.contains("5.5 s"));
        assertTrue(html.contains("44.1 K"));
        assertTrue(html.contains("106"));
        assertTrue(html.contains("38.6%"));
        assertTrue("sample loss should be flagged", html.contains("class='fig bad'"));
    }

    /** The meter is what turns a bare percentage into something with a sense of scale. */
    @Test
    public void sampleLossCarriesAMeter() {
        assertTrue(document(ready(List.of(), List.of())).contains("<div class='meter'><i style='width:39%'>"));
    }

    @Test
    public void findingsAreTitledByTheRuleThatFired() {
        String html = document(ready(List.of(
                new RecordingState.Finding("GC Pauses", "WARNING", "Application efficiency was affected by GC pauses."),
                new RecordingState.Finding("Thrown Errors", "WARNING", "174 errors per minute.")), List.of()));

        assertTrue(html.contains("<div class='rn'>GC Pauses</div>"));
        assertTrue(html.contains("<div class='sm'>Application efficiency was affected by GC pauses.</div>"));
        assertTrue(html.contains("<div class='rn'>Thrown Errors</div>"));
        assertTrue(html.contains("2 findings"));
    }

    /** A rule that arrived blank must still produce a titled row rather than an empty bold line. */
    @Test
    public void aFindingWithNoRuleStillGetsATitle() {
        String html = document(ready(List.of(
                new RecordingState.Finding("  ", "WARNING", "Something happened.")), List.of()));

        assertTrue(html.contains("<div class='rn'>Finding</div>"));
        assertTrue(html.contains("1 finding"));
        assertFalse("one finding must not read as plural", html.contains("1 findings"));
    }

    /**
     * A run that failed. Microscope runs the rules alongside the parse and waits for them, so a ready
     * profile without findings is not one that is still working — and the honest offer is another run.
     * The heading survives with the count dropped; a vanished section reads as a rendering bug.
     */
    @Test
    public void anUncomputedAnalysisKeepsItsHeadingAndOffersToRunIt() {
        String html = document(withSummary(new RecordingState.ProfileSummary(
                RecordingState.Kind.RECORDING, "profile",
                new RecordingState.RecordingFigures(5_539, 44_099, 106, 353, 222),
                null, false, true, List.of(), List.of())));

        assertTrue(html.contains("Auto-analysis"));
        assertTrue(html.contains("The analysis rules did not run for this profile."));
        assertTrue(html.contains("data-action='view:auto-analysis'"));
        assertFalse("nothing is running, so nothing spins", html.contains("class='callout'"));
    }

    /** A recording Microscope no longer has. There is no run left to offer, so none is offered. */
    @Test
    public void anAnalysisThatCannotRunOffersNothingToPress() {
        String html = document(withSummary(new RecordingState.ProfileSummary(
                RecordingState.Kind.RECORDING, "profile",
                new RecordingState.RecordingFigures(5_539, 44_099, 106, 353, 222),
                null, false, false, List.of(), List.of())));

        assertTrue(html.contains("Auto-analysis"));
        assertTrue(html.contains("Microscope no longer has the recording file."));
        assertFalse(html.contains("Run it in Microscope"));
    }

    /** A recording the rules cleared is a result, and used to be reported as an absence. */
    @Test
    public void anAnalysisThatFlaggedNothingSaysSoRatherThanLookingUnfinished() {
        String html = document(withSummary(new RecordingState.ProfileSummary(
                RecordingState.Kind.RECORDING, "profile",
                new RecordingState.RecordingFigures(5_539, 44_099, 106, 353, 222),
                null, true, true, List.of(), List.of())));

        assertTrue(html.contains("Nothing flagged."));
        assertTrue(html.contains("0 findings"));
        assertFalse(html.contains("The analysis rules did not run"));
    }

    @Test
    public void gatedViewsAreDimmedRatherThanClickable() {
        String html = document(ready(List.of(), List.of("SUBSECOND")));

        assertTrue(html.contains("class='card off'"));
        assertTrue(html.contains("Not in this recording"));
        assertFalse("a gated view must carry no action",
                html.contains("data-action='view:subsecond/primary'"));
        assertTrue("an ungated view must", html.contains("data-action='view:flamegraphs/primary'"));
    }

    /** A dump's verdict is Leak suspects, which leads its grid — an empty findings section is noise. */
    @Test
    public void aHeapDumpGetsHeapFiguresAndNoFindingsSection() {
        String html = document(heapDump(true));

        assertTrue(html.contains("retained"));
        assertTrue(html.contains("GC roots"));
        assertFalse(html.contains("Auto-analysis"));
        assertTrue(html.contains("data-action='view:heap-dump/leak-suspects'"));
    }

    /** Four zeroes would read as facts. They are not. */
    @Test
    public void anUnindexedHeapDumpSaysSoInsteadOfPrintingZeroes() {
        String html = document(heapDump(false));

        assertFalse(html.contains("class='figs'"));
        assertTrue(html.contains("The index has not been built"));
        assertTrue("the remedy sits beside the sentence", html.contains("data-action='build-index'"));
        assertTrue(html.contains("profile ready"));
    }

    /**
     * Every view of an un-indexed dump opens an empty page, so no tile may be a button. They stay
     * drawn — dashed and dim, keeping their own blurbs — because they are the views the build brings.
     */
    @Test
    public void anUnindexedHeapDumpLocksEveryTile() {
        String html = document(heapDump(false));

        assertFalse(html.contains("data-action='view:"));
        assertEquals(ProfileView.HEAP.size(), count(html, "class='card off'"));
        assertTrue("the tile keeps its own blurb", html.contains("What is holding the memory"));
        assertFalse("not the no-data blurb", html.contains("Not in this recording"));
    }

    /** The same box becomes the progress report: stage, of how many, and no second Build button. */
    @Test
    public void aRunningIndexBuildTurnsTheCalloutIntoProgress() {
        String html = document(heapDump(false).withBuild(
                new PipelineBuild(PipelineBuild.Pipeline.HEAP_INDEX, PipelineBuild.Phase.RUNNING, 5, 13, "dominator", 80_000L, null)));

        assertTrue(html.contains("class='spin'"));
        assertTrue(html.contains("stage 5 of 13"));
        assertTrue(html.contains("Dominator tree"));
        assertTrue(html.contains("1 m 20 s elapsed"));
        assertTrue(html.contains("building the index"));
        assertFalse(html.contains("data-action='build-index'"));
        assertTrue("the tiles stay drawn", html.contains("Leak suspects"));
        assertFalse("but locked until the build is done", html.contains("data-action='view:"));
    }

    @Test
    public void aFailedIndexBuildKeepsTheBoxAndOffersToGoAgain() {
        String html = document(heapDump(false).withBuild(
                new PipelineBuild(PipelineBuild.Pipeline.HEAP_INDEX, PipelineBuild.Phase.FAILED, 2, 13, "parse", 9_000L, "Java <heap> space")));

        assertTrue(html.contains("class='callout bad'"));
        assertTrue(html.contains("The index build failed"));
        assertTrue("Microscope's words, escaped", html.contains("Java &lt;heap&gt; space"));
        assertTrue(html.contains("data-action='build-index'"));
        assertFalse(html.contains("class='spin'"));
    }

    /** The header well says what the file is: the flame for a recording, the object graph for a dump. */
    @Test
    public void theHeaderWellDrawsTheObjectGraphForAHeapDumpAndTheFlameForARecording() {
        String heap = PanelSvg.icon("heap", "ico ico-lg");
        String flame = PanelSvg.icon("flame", "ico ico-lg");

        assertTrue(document(heapDump(true)).contains(heap));
        assertFalse(document(heapDump(true)).contains(flame));
        assertTrue(document(ready(List.of(), List.of())).contains(flame));
        assertFalse(document(ready(List.of(), List.of())).contains(heap));
    }

    /** Before Microscope has answered, the file name alone decides the glyph. */
    @Test
    public void anUnanalysedHprofAlreadyWearsTheObjectGraph() {
        RecordingState dump = new RecordingState(
                RecordingState.Status.NOT_IMPORTED, null, null, "service.hprof", 2_300_000_000L, null);

        assertTrue(document(dump).contains(PanelSvg.icon("heap", "ico ico-lg")));
        assertTrue(document(notImported()).contains(PanelSvg.icon("flame", "ico ico-lg")));
    }

    /** An indexed dump has figures; the callout has nothing to say and must not appear. */
    @Test
    public void anIndexedHeapDumpShowsFiguresAndNoCallout() {
        String html = document(heapDump(true));

        assertTrue(html.contains("class='figs'"));
        assertFalse(html.contains("class='callout"));
    }

    /** The not-indexed sentence is about heap dumps; a recording without figures must not claim it. */
    @Test
    public void aRecordingWithNoFiguresDoesNotBorrowTheHeapMessage() {
        String html = document(withSummary(new RecordingState.ProfileSummary(
                RecordingState.Kind.RECORDING, "profile", null, null, true, true, List.of(), List.of())));

        assertFalse(html.contains("has not been indexed yet"));
        assertFalse(html.contains("class='figs'"));
        assertTrue("the views must still be offered", html.contains("data-action='view:flamegraphs/primary'"));
    }

    @Test
    public void neverAnalysedOffersTheAnalyzeButtonAndTheFacts() {
        String html = document(notImported());

        assertTrue(html.contains("data-action='analyze'"));
        assertTrue(html.contains("Not analysed yet"));
        assertTrue(html.contains("<dt>Path</dt>"));
        assertFalse("nothing exists yet, so no views may be offered", html.contains("class='views'"));
    }

    @Test
    public void analyzingOffersOnlyACheck() {
        String html = document(analyzing());

        assertTrue(html.contains("data-action='check'"));
        assertTrue(html.contains("class='prog'"));
        assertFalse(html.contains("data-action='analyze'"));
    }

    /**
     * Once Microscope has named the run, the bar that never moved gives way to the same box the index
     * build draws. A recording can take minutes to parse, and an indeterminate bar for all of it is
     * indistinguishable from one that has hung.
     */
    @Test
    public void analyzingShowsTheStageOnceThereIsOneToShow() {
        String html = document(analyzing().withBuild(new PipelineBuild(
                PipelineBuild.Pipeline.PROFILE_INIT,
                PipelineBuild.Phase.RUNNING, 2, 9, "parse", 12_000L, null)));

        assertTrue(html.contains("stage 2 of 9"));
        assertTrue("the recording's own word for it, not the heap dump's",
                html.contains("Reading the recording"));
        assertFalse("which is what a shared title map would have said", html.contains("Parsing objects"));
        assertTrue(html.contains("12.0 s elapsed"));
        assertTrue("still the only thing to press", html.contains("data-action='check'"));
    }

    /** No run named yet: the indeterminate bar is still the honest drawing. */
    @Test
    public void analyzingKeepsThePlainBarUntilMicroscopeNamesTheRun() {
        String html = document(analyzing());

        assertTrue(html.contains("class='prog'"));
        assertFalse(html.contains("stage "));
    }

    /** The accent goes neutral when Microscope did not answer, so the panel reads as wrong. */
    @Test
    public void unreachableMutesTheAccentAndOffersSettings() {
        String html = document(unavailable());

        assertTrue(html.contains("class='accent mute'"));
        assertTrue(html.contains("class='well mute'"));
        assertTrue(html.contains("data-action='retry'"));
        assertTrue(html.contains("data-action='settings'"));
    }

    @Test
    public void readyStatesKeepTheFlameAccent() {
        assertTrue(document(ready(List.of(), List.of())).contains("<div class='accent'></div>"));
    }

    // --- the agent split button ---------------------------------------------------------------

    @Test
    public void theSplitButtonRunsThePrimaryAgentAndHidesTheRest() {
        String html = document(ready(List.of(), List.of()),
                new AgentRow(List.of(
                        new AgentRow.Entry(CLAUDE, true),
                        new AgentRow.Entry(CODEX, false)), CLAUDE));

        assertTrue(html.contains("data-action='agent:claude'"));
        assertTrue(html.contains("Analyse with Claude"));
        assertTrue("an uninstalled agent stays visible in the menu", html.contains("Not on PATH"));
        assertTrue(html.contains("<span class='tail'>codex</span>"));
        assertFalse("but it must not be clickable", html.contains("data-action='agent:codex'"));
    }

    @Test
    public void withNothingInstalledTheSplitButtonIsDisabled() {
        String html = document(ready(List.of(), List.of()),
                new AgentRow(List.of(
                        new AgentRow.Entry(CLAUDE, false),
                        new AgentRow.Entry(CODEX, false)), null));

        assertTrue(html.contains("Analyse with an agent"));
        assertFalse(html.contains("data-action='agent:"));
        assertTrue(html.contains("Not on PATH"));
    }

    /** A switch the developer turned off should not leave a disabled control inviting a hunt. */
    @Test
    public void agentsDisabledRemovesTheSplitButtonEntirely() {
        String html = WebPanelHtml.document(new WebPanelHtml.Content(panel(ready(List.of(), List.of())), FILE, URL, row(), false), "");

        assertFalse(html.contains("Analyse with"));
        // The delegated listener always mentions data-menu; the agent split button's own markup is
        // what must be gone. Anchors as such are not the test — the baseline menu is one too, and it
        // answers to a different switch.
        assertFalse(html.contains("aria-label='More agents'"));
        assertFalse(html.contains("data-action='agent:"));
        assertTrue("the open button must survive", html.contains("data-action='open'"));
    }

    // --- comparing ------------------------------------------------------------------------------

    /**
     * Which file is the baseline is the half of a comparison that cannot be dropped: read the other
     * way round, every regression reports as an improvement.
     */
    @Test
    public void comparingNamesTheBaselineAndKeepsThePrimaryInTheHeader() {
        String html = document(comparing());

        assertTrue(html.contains("Baseline"));
        assertTrue(html.contains("jeffrey-baseline.jfr"));
        // the tab is still about its own recording
        assertTrue(html.contains("jeffrey-20260904-180108"));
        assertTrue(html.contains("compared against a baseline"));
    }

    /** Both sides' own numbers, never a delta: the delta is Microscope's to compute. */
    @Test
    public void comparingPutsTheBaselinesFiguresUnderThePrimarys() {
        String html = document(panel(ready(List.of(), List.of()))
                .withBaseline(otherRecording(4 * 60_000L, 106)));

        assertTrue(html.contains("class='bl'"));
        assertTrue("the baseline's window belongs under the primary's", html.contains("4 m"));
        // Two facts, never their difference: a delta needs the scaling and pruning Microscope does,
        // and a signed number here would be a claim the panel has not earned.
        assertFalse("a delta is not the panel's to state", figuresBlock(html).contains("+"));
        assertFalse(figuresBlock(html).contains("−"));
    }

    @Test
    public void comparingOffersTheDifferentialViewsAndNothingElse() {
        String html = document(comparing());

        for (ProfileView view : ProfileView.DIFFERENTIAL) {
            assertTrue("no tile for " + view.label(), html.contains("data-action='view:" + view.path() + "'"));
        }
        assertFalse("the single-profile flame graph has no place in a comparison",
                html.contains("data-action='view:flamegraphs/primary'"));
    }

    /** A comparison is about the pair; the findings are about one recording. */
    @Test
    public void comparingReplacesTheFindingsWithTheVerdict() {
        RecordingState flagged = ready(
                List.of(new RecordingState.Finding("GC Pauses", "WARNING", "Long pauses.")), List.of());
        String html = document(panel(flagged).withBaseline(otherRecording(5_539, 106)));

        assertTrue(html.contains("Comparability"));
        assertFalse(html.contains("GC Pauses"));
    }

    /** The caution a reader has to see before the numbers, not after. */
    @Test
    public void differingWindowsAreCalledOutAboveTheTiles() {
        String html = document(panel(ready(List.of(), List.of()))
                .withBaseline(otherRecording(4 * 60_000L, 87)));

        assertTrue(html.contains("class='callout'"));
        assertTrue(html.contains("not a like-for-like pair"));
        // and the two figures at fault are the ones coloured
        assertTrue(html.contains("class='fig bad'"));
    }

    @Test
    public void aComparableePairIsStatedAsALineRatherThanACallout() {
        String html = document(comparing());

        assertTrue(html.contains("Comparability"));
        assertFalse(html.contains("class='callout'"));
        assertTrue(html.contains("the recordings cannot say"));
    }

    /** Swap and Clear live with the baseline, which is the only place the pair can be changed. */
    @Test
    public void comparingOffersSwapAndClear() {
        String html = document(comparing());

        assertTrue(html.contains("data-action='swap'"));
        assertTrue(html.contains("data-action='uncompare'"));
    }

    /** Swapping to a baseline with no profile would leave the tab pointing at nothing. */
    @Test
    public void aBaselineStillImportingCanBeClearedButNotSwapped() {
        PanelState state = panel(ready(List.of(), List.of())).withBaseline(new RecordingState(
                RecordingState.Status.ANALYZING, "rec-2", null, "jeffrey-baseline.jfr", 8_000_000L, null));
        String html = document(state);

        assertTrue(html.contains("data-action='uncompare'"));
        assertFalse(html.contains("data-action='swap'"));
        assertTrue(html.contains("building its profile"));
    }

    /** The agent runs a different skill on a pair, and the wording is what picks it. */
    @Test
    public void theAgentButtonSaysCompareWhileComparing() {
        assertTrue(document(comparing()).contains("Compare with Claude"));
        assertFalse(document(comparing()).contains("Analyse with Claude"));
        assertTrue(document(ready(List.of(), List.of())).contains("Analyse with Claude"));
    }

    /**
     * The verdict and the links have to agree. A pair Microscope cannot subtract still gets its
     * callout, but every tile keeps pointing at pages that can actually be drawn.
     */
    @Test
    public void anIncomparablePairKeepsTheProfilesOwnTiles() {
        RecordingState noFigures = new RecordingState(
                RecordingState.Status.READY, "rec-2", "profile-2", "jeffrey-baseline.jfr", 8_000_000L,
                new RecordingState.ProfileSummary(
                        RecordingState.Kind.RECORDING, "baseline", null, null,
                        true, true, List.of(), List.of()));
        String html = document(panel(ready(List.of(), List.of())).withBaseline(noFigures));

        assertTrue(html.contains("cannot be compared"));
        assertTrue(html.contains("data-action='view:flamegraphs/primary'"));
        assertFalse(html.contains("data-action='view:flamegraphs/differential'"));
        // and the baseline is still named, so it can be cleared
        assertTrue(html.contains("data-action='uncompare'"));
    }

    // --- picking a baseline ---------------------------------------------------------------------

    /**
     * The figures are in the menu so the choice is informed. Picking blind is how a four-minute run
     * ends up as the baseline of a twenty-minute one, and the caution afterwards arrives too late.
     */
    @Test
    public void theBaselineMenuListsCandidatesWithTheirFigures() {
        String html = document(panel(ready(List.of(), List.of())).withCandidates(List.of(
                new CompareCandidate(Path.of("/recordings/before.jfr"), otherRecording(16 * 60_000L, 99)))));

        assertTrue(html.contains("Compare with…"));
        assertTrue(html.contains("data-action='compare:/recordings/before.jfr'"));
        assertTrue(html.contains("Ready in Microscope"));
        assertTrue(html.contains("16 m"));
    }

    /** A recording Microscope has never seen is still offered: the panel knows how to import it. */
    @Test
    public void aCandidateWithNoProfileIsOfferedAndSaysItImportsFirst() {
        RecordingState unseen = new RecordingState(
                RecordingState.Status.NOT_IMPORTED, null, null, "before.jfr", 4_000L, null);
        String html = document(panel(ready(List.of(), List.of())).withCandidates(
                List.of(new CompareCandidate(Path.of("/recordings/before.jfr"), unseen))));

        assertTrue(html.contains("Not analysed yet"));
        assertTrue(html.contains("imports first"));
        assertTrue("it must still be clickable", html.contains("data-action='compare:/recordings/before.jfr'"));
    }

    /** Once a pair exists the strip carries the choice; a second control for it would be a second truth. */
    @Test
    public void theBaselineMenuIsGoneOnceOneIsPicked() {
        String html = document(comparing().withCandidates(List.of(
                new CompareCandidate(Path.of("/recordings/before.jfr"), otherRecording(5_539, 106)))));

        assertFalse(html.contains("Compare with…"));
        assertFalse(html.contains("data-action='compare:"));
    }

    /**
     * Every action the document can emit has to be one {@link CefPanelRenderer} routes.
     *
     * <p>Nothing else catches this. An action the renderer does not know reaches the log and the
     * click does nothing — a dead button that looks exactly like a working one, which is how the
     * comparison's own three actions were first shipped unrouted. The vocabulary is small enough to
     * list, so it is listed: adding to it here without adding to the dispatcher fails this test.
     */
    @Test
    public void theDocumentEmitsOnlyActionsTheRendererRoutes() {
        Set<String> emitted = new TreeSet<>();
        for (String html : everyState()) {
            Matcher matcher = Pattern.compile("data-action='([^':]+):?").matcher(html);
            while (matcher.find()) {
                emitted.add(matcher.group(1));
            }
        }

        assertEquals(
                Set.of("agent", "analyze", "build-index", "check", "compare", "open", "retry",
                        "settings", "swap", "uncompare", "view"),
                emitted);
    }

    /**
     * Every state's document closes every element it opens.
     *
     * <p>The panel is built by concatenating strings, so a missing {@code </div>} is a compile-clean
     * mistake that reaches the developer as a collapsed layout — the figures and the tiles adopted
     * as flex children of the header row, laid out beside it instead of under it. The comparison
     * states nest deepest, which is where it would happen.
     */
    @Test
    public void everyStatesDocumentIsBalanced() {
        for (String html : everyState()) {
            int depth = 0;
            Matcher matcher = Pattern.compile("<(/?)div\\b").matcher(html);
            while (matcher.find()) {
                depth += matcher.group(1).isEmpty() ? 1 : -1;
                assertTrue("a </div> closes an element that was never opened", depth >= 0);
            }
            assertEquals("unclosed <div> elements", 0, depth);
        }
    }

    /** One document per panel state, so the sweep above sees every button the panel can draw. */
    private static List<String> everyState() {
        RecordingState dump = heapDump(false);
        return List.of(
                document(ready(List.of(), List.of())),
                document(panel(ready(List.of(), List.of())).withCandidates(List.of(
                        new CompareCandidate(Path.of("/recordings/before.jfr"), otherRecording(5_539, 106))))),
                document(comparing()),
                document(dump),
                document(notImported()),
                document(analyzing()),
                document(unavailable()),
                WebPanelHtml.failure("boom", "run.jfr", 1024L, ""));
    }

    // --- the rest -------------------------------------------------------------------------------

    @Test
    public void theBridgeScriptIsCarriedIntoTheDocument() {
        String html = WebPanelHtml.document(
                new WebPanelHtml.Content(panel(ready(List.of(), List.of())), FILE, URL, row(), true),
                "window.__jeffrey=function(a){/*bridge*/};");

        assertTrue(html.contains("/*bridge*/"));
        assertTrue("the page must call what the bridge defines", html.contains("window.__jeffrey("));
    }

    /** No href anywhere is what makes it impossible for a click to navigate the panel away. */
    @Test
    public void theDocumentCarriesNoLinks() {
        assertFalse(document(ready(List.of(), List.of())).contains("<a "));
        assertFalse(document(heapDump(true)).contains("href="));
    }

    @Test
    public void failureShowsTheMessageAndOffersSettings() {
        String html = WebPanelHtml.failure("Unexpected chunk magic", "run.jfr", 8_450_244L, "");

        assertTrue(html.contains("Unexpected chunk magic"));
        assertTrue(html.contains("The analysis did not finish"));
        assertTrue(html.contains("data-action='settings'"));
        assertTrue(html.contains("class='accent mute'"));
    }

    @Test
    public void loadingSaysWhatItIsWaitingFor() {
        assertTrue(WebPanelHtml.loading("").contains("Asking Microscope about this file"));
    }

    /** A quote in a file name must not close an attribute early. */
    @Test
    public void awkwardNamesAreEscaped() {
        RecordingState awkward = new RecordingState(
                RecordingState.Status.NOT_IMPORTED, null, null, "run'<x>.jfr", 12L, null);
        String html = document(awkward);

        assertFalse(html.contains("run'<x>"));
        assertTrue(html.contains("run&#39;&lt;x&gt;.jfr"));
    }

    @Test
    public void everyStatusRendersSomething() {
        for (RecordingState.Status status : RecordingState.Status.values()) {
            RecordingState state = new RecordingState(status, null, null, "run.jfr", 12L, null);
            String html = document(state);
            assertTrue(status + " must render a body", html.contains("<body>"));
            assertFalse(status + " must not render an empty body", html.contains("<body></body>"));
        }
    }

    // --- fixtures -------------------------------------------------------------------------------

    private static int count(String html, String needle) {
        int n = 0;
        int at = html.indexOf(needle);
        while (at >= 0) {
            n++;
            at = html.indexOf(needle, at + needle.length());
        }
        return n;
    }

    private static String document(RecordingState state) {
        return document(state, row());
    }

    private static String document(RecordingState state, AgentRow agents) {
        return document(panel(state), agents);
    }

    private static String document(PanelState state) {
        return document(state, row());
    }

    private static String document(PanelState state, AgentRow agents) {
        return WebPanelHtml.document(
                new WebPanelHtml.Content(state, FILE, URL, agents, true), "");
    }

    /** A second recording, so a pair can be built without every test spelling one out. */
    private static RecordingState otherRecording(long durationMillis, int eventTypes) {
        RecordingState.ProfileSummary summary = new RecordingState.ProfileSummary(
                RecordingState.Kind.RECORDING, "jeffrey-baseline",
                new RecordingState.RecordingFigures(durationMillis, 40_000, eventTypes, 353, 222),
                null, true, true, List.of(), List.of());
        return new RecordingState(
                RecordingState.Status.READY, "rec-2", "profile-2",
                "jeffrey-baseline.jfr", 8_000_000L, summary);
    }

    private static PanelState comparing() {
        return panel(ready(List.of(), List.of())).withBaseline(otherRecording(5_539, 106));
    }

    /** The row of figure tiles, so an assertion about them cannot be answered by the stylesheet. */
    private static String figuresBlock(String html) {
        int start = html.indexOf("<div class='figs'>");
        int end = html.indexOf("<div class='body'>", start);
        return html.substring(start, end);
    }

    private static AgentRow row() {
        return new AgentRow(List.of(new AgentRow.Entry(CLAUDE, true)), CLAUDE);
    }

    private static RecordingState ready(List<RecordingState.Finding> findings, List<String> disabled) {
        return withSummary(new RecordingState.ProfileSummary(
                RecordingState.Kind.RECORDING, "jeffrey-20260904-180108",
                new RecordingState.RecordingFigures(5_539, 44_099, 106, 353, 222),
                null, true, true, findings, disabled));
    }

    private static RecordingState heapDump(boolean cacheReady) {
        return withSummary(new RecordingState.ProfileSummary(
                RecordingState.Kind.HEAP_DUMP, "microscope.hprof", null,
                new RecordingState.HeapFigures(34_536_952L, 737_553L, 15_474, 4_700, cacheReady),
                false, false, List.of(), List.of()));
    }

    private static RecordingState withSummary(RecordingState.ProfileSummary summary) {
        return new RecordingState(
                RecordingState.Status.READY, "rec-1", "profile-1",
                "jeffrey-20260904-180108.jfr", 8_450_244L, summary);
    }

    private static RecordingState notImported() {
        return new RecordingState(
                RecordingState.Status.NOT_IMPORTED, null, null,
                "jeffrey-20260904-180108.jfr", 8_450_244L, null);
    }

    private static RecordingState analyzing() {
        return new RecordingState(
                RecordingState.Status.ANALYZING, null, null,
                "jeffrey-20260904-180108.jfr", 8_450_244L, null);
    }

    private static RecordingState unavailable() {
        return RecordingState.unavailable("jeffrey-20260904-180108.jfr", 8_450_244L);
    }
}
