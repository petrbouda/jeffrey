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
import cafe.jeffrey.ide.plugin.idea.recording.RecordingState;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

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

    /** The heading survives with the count dropped; a vanished section reads as a rendering bug. */
    @Test
    public void anUncomputedAnalysisKeepsItsHeadingAndOffersToRunIt() {
        String html = document(withSummary(new RecordingState.ProfileSummary(
                RecordingState.Kind.RECORDING, "profile",
                new RecordingState.RecordingFigures(5_539, 44_099, 106, 353, 222),
                null, false, List.of(), List.of())));

        assertTrue(html.contains("Auto-analysis"));
        assertTrue(html.contains("Not computed for this profile yet."));
        assertTrue(html.contains("data-action='view:auto-analysis'"));
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
        assertTrue(html.contains("has not been indexed yet"));
    }

    /** The not-indexed sentence is about heap dumps; a recording without figures must not claim it. */
    @Test
    public void aRecordingWithNoFiguresDoesNotBorrowTheHeapMessage() {
        String html = document(withSummary(new RecordingState.ProfileSummary(
                RecordingState.Kind.RECORDING, "profile", null, null, true, List.of(), List.of())));

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
        String html = WebPanelHtml.document(new WebPanelHtml.Content(
                ready(List.of(), List.of()), FILE, URL, row(), false), "");

        assertFalse(html.contains("Analyse with"));
        // The delegated listener always mentions data-menu; the split button's own markup is what
        // must be gone.
        assertFalse(html.contains("aria-label='More agents'"));
        assertFalse(html.contains("class='anchor'"));
        assertTrue("the open button must survive", html.contains("data-action='open'"));
    }

    // --- the rest -------------------------------------------------------------------------------

    @Test
    public void theBridgeScriptIsCarriedIntoTheDocument() {
        String html = WebPanelHtml.document(
                new WebPanelHtml.Content(ready(List.of(), List.of()), FILE, URL, row(), true),
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

    private static String document(RecordingState state) {
        return document(state, row());
    }

    private static String document(RecordingState state, AgentRow agents) {
        return WebPanelHtml.document(
                new WebPanelHtml.Content(state, FILE, URL, agents, true), "");
    }

    private static AgentRow row() {
        return new AgentRow(List.of(new AgentRow.Entry(CLAUDE, true)), CLAUDE);
    }

    private static RecordingState ready(List<RecordingState.Finding> findings, List<String> disabled) {
        return withSummary(new RecordingState.ProfileSummary(
                RecordingState.Kind.RECORDING, "jeffrey-20260904-180108",
                new RecordingState.RecordingFigures(5_539, 44_099, 106, 353, 222),
                null, true, findings, disabled));
    }

    private static RecordingState heapDump(boolean cacheReady) {
        return withSummary(new RecordingState.ProfileSummary(
                RecordingState.Kind.HEAP_DUMP, "microscope.hprof", null,
                new RecordingState.HeapFigures(34_536_952L, 737_553L, 15_474, 4_700, cacheReady),
                false, List.of(), List.of()));
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
