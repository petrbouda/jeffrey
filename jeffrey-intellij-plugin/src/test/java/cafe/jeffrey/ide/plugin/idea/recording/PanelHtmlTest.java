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

import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The markup the panel renders. Worth testing directly because every mistake here is silent: a tile
 * linked when it should be dimmed, a figure printed as a bare zero, an apostrophe in a file name
 * closing an attribute early — none of it throws, all of it reaches the developer as a wrong panel.
 */
public class PanelHtmlTest {

    private static final Path FILE = Path.of("/home/dev/jeffrey/jeffrey-20260904-180108.jfr");
    private static final String URL = "http://localhost:8585";

    @Test
    public void headerCarriesTheFourFiguresWhenReady() {
        String html = PanelHtml.header(ready(List.of()), FILE, URL);

        assertTrue(html.contains("Profile ready"));
        assertTrue(html.contains("5.5 s"));
        assertTrue(html.contains("44.1 K"));
        assertTrue(html.contains("106"));
        assertTrue(html.contains("38.6%"));
    }

    /** Before there is a profile there are no figures — and none must be invented as zeroes. */
    @Test
    public void headerCarriesNoFiguresBeforeThereIsAProfile() {
        String html = PanelHtml.header(notImported(), FILE, URL);

        assertTrue(html.contains("Flight recording"));
        assertTrue(html.contains("Not analysed yet"));
        assertFalse(html.contains("class='num'"));
    }

    /**
     * Colouring the loss figure is reserved for a measured loss. A recording that reports no sampler
     * health at all would otherwise be painted red for a number it never produced.
     */
    @Test
    public void coloursOnlyAMeasuredSampleLoss() {
        assertTrue(PanelHtml.header(ready(List.of()), FILE, URL).contains("num alarm"));

        RecordingState silent = withSummary(summary(0, 0, true, List.of()));
        String html = PanelHtml.header(silent, FILE, URL);
        assertTrue(html.contains("not reported"));
        assertFalse(html.contains("num alarm"));
    }

    @Test
    public void tilesLinkEveryAvailableViewByItsPath() {
        String html = PanelHtml.details(ready(List.of()), FILE, URL);

        for (ProfileView view : ProfileView.ALL) {
            assertTrue("no tile for " + view.label(), html.contains(view.label()));
            assertTrue("no link for " + view.label(), html.contains("href='" + view.path() + "'"));
            assertTrue("no icon for " + view.label(), html.contains("<icon src='" + view.iconKey() + "'/>"));
        }
    }

    /**
     * The reason the panel asks Microscope for the disabled list at all. A view with no data keeps its
     * tile — dashed and unlinked — because a missing tile teaches nothing, while a dimmed one says the
     * recording lacks that data.
     */
    @Test
    public void dimsAndUnlinksAViewTheRecordingHasNoDataFor() {
        String html = PanelHtml.details(ready(List.of("TRACES")), FILE, URL);

        assertTrue(html.contains("tile off"));
        assertTrue(html.contains("Not in this recording"));
        assertFalse(html.contains("href='traces/operations'"));
        // still present, still named
        assertTrue(html.contains("Traces"));
        // and nothing else was dimmed by association
        assertTrue(html.contains("href='allocations'"));
    }

    @Test
    public void saysAnalysisIsNotComputedRatherThanShowingNothingWrong() {
        String html = PanelHtml.details(ready(List.of()), FILE, URL);

        assertTrue(html.contains("Not computed for this profile yet."));
        assertTrue(html.contains("Run it in Microscope"));
    }

    @Test
    public void listsFindingsAndMarksTheWarnings() {
        RecordingState state = withSummary(summary(353, 222, true, List.of(
                new RecordingState.Finding("gc-pauses", "WARNING", "Long GC pauses"),
                new RecordingState.Finding("sampler", "OK", "No sample loss"))));

        String html = PanelHtml.details(state, FILE, URL);

        assertTrue(html.contains("class='warn'>Long GC pauses"));
        assertTrue(html.contains("class=''>No sample loss"));
    }

    /** Before there is a profile the pane shows the file's own facts, which are true either way. */
    @Test
    public void showsTheFilesOwnFactsBeforeItIsAnalysed() {
        String html = PanelHtml.details(notImported(), FILE, URL);

        assertTrue(html.contains("jeffrey-20260904-180108.jfr"));
        assertTrue(html.contains("/home/dev/jeffrey"));
        assertTrue(html.contains(URL));
        assertFalse(html.contains("tile"));
    }

    /**
     * File names are not this plugin's to trust. An apostrophe closes an attribute and an angle
     * bracket opens a tag, and both appear in real file names.
     */
    @Test
    public void escapesTextThatComesFromOutside() {
        assertEquals("a &amp; b", PanelHtml.escape("a & b"));
        assertEquals("&lt;b&gt;", PanelHtml.escape("<b>"));
        assertEquals("it&#39;s", PanelHtml.escape("it's"));
        assertEquals("", PanelHtml.escape(null));

        RecordingState awkward = new RecordingState(
                RecordingState.Status.NOT_IMPORTED, null, null, "run'<x>.jfr", 1024, null);
        String html = PanelHtml.details(awkward, Path.of("/tmp/run'<x>.jfr"), URL);
        assertFalse(html.contains("run'<x>"));
        assertTrue(html.contains("run&#39;&lt;x&gt;.jfr"));
    }

    // --- fixtures -----------------------------------------------------------------------------

    private static RecordingState ready(List<String> disabledFeatures) {
        return withSummary(new RecordingState.ProfileSummary(
                "jeffrey-20260904-180108", 5_539, 44_099, 106, 353, 222, false, List.of(), disabledFeatures));
    }

    private static RecordingState.ProfileSummary summary(
            long captured, long lost, boolean computed, List<RecordingState.Finding> findings) {

        return new RecordingState.ProfileSummary(
                "jeffrey-20260904-180108", 5_539, 44_099, 106, captured, lost, computed, findings, List.of());
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
}
