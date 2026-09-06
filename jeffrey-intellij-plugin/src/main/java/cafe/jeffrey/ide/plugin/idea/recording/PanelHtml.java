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

import java.nio.file.Path;
import java.util.List;

/**
 * The panel's two documents, as markup.
 *
 * <p>Pure string building, with no Swing in sight, so the part that is easy to get wrong — which tile
 * is dimmed, what a missing figure prints, whether a filename with an ampersand in it escapes — is
 * testable without an IDE fixture. The panel only decides where to put the results.
 *
 * <p>Written against what Swing's HTML engine actually renders: tables for layout, {@code <hr>} for
 * rules, {@code <icon src>} for icons, and no flexbox, grid, border-radius or hover anywhere. The
 * class names refer to rules {@link PanelStyles} defines.
 */
final class PanelHtml {

    /** Three per row, matching {@link ProfileView#COLUMNS}, expressed as the width each cell claims. */
    private static final String TILE_WIDTH = "33%";

    private static final String NOT_COMPUTED =
            "Not computed for this profile yet.";

    private static final String NOT_INDEXED =
            "This heap dump has not been indexed yet — open it in Microscope to build the index.";

    private PanelHtml() {
    }

    /**
     * The top pane: what this file is, and — once there is a profile — the four figures.
     *
     * <p>The same shape in every state, so the tab does not appear to rearrange itself while an
     * analysis runs. Only the words and the presence of the figures change.
     */
    static String header(RecordingState state, Path file, String microscopeUrl) {
        StringBuilder html = new StringBuilder(512);
        html.append("<html><body>");
        html.append("<table><tr><td width='34'><icon src='flame'/></td><td>")
                .append("<span class='big'>").append(escape(title(state))).append("</span><br>")
                .append("<span class='sml'>").append(escape(subtitle(state, file, microscopeUrl))).append("</span>")
                .append("</td></tr></table>");

        RecordingState.ProfileSummary summary = state.summary();
        if (summary != null) {
            html.append("<table><tr>").append(figures(summary)).append("</tr></table>");
        }
        return html.append("</body></html>").toString();
    }

    /**
     * The bottom pane: the findings and the tiles once a profile exists, the file's own facts before
     * then. Both are things the reader can act on; neither is a figure the recording produced.
     */
    static String details(RecordingState state, Path file, String microscopeUrl) {
        StringBuilder html = new StringBuilder(2048);
        html.append("<html><body>");

        RecordingState.ProfileSummary summary = state.summary();
        if (summary == null) {
            return html.append(facts(state, file, microscopeUrl)).append("</body></html>").toString();
        }

        // Auto-analysis is a recording's verdict. A heap dump's is "Leak suspects", which leads its
        // tile grid — so a dump gets no findings section rather than an empty one.
        if (!summary.isHeapDump()) {
            html.append(section("Auto-analysis")).append(findings(summary));
        }
        html.append(section("Open a view")).append(tiles(summary));
        return html.append("</body></html>").toString();
    }

    /**
     * Four figures, chosen by what the profile holds. A heap dump has no recording window and a
     * recording has no retained size; showing one set for both would print numbers that mean nothing.
     */
    private static String figures(RecordingState.ProfileSummary summary) {
        if (summary.isHeapDump()) {
            return heapFigures(summary.heap());
        }
        return recordingFigures(summary.recording());
    }

    private static String recordingFigures(RecordingState.RecordingFigures figures) {
        if (figures == null) {
            return "";
        }
        return stat(Formats.duration(figures.durationInMillis()), "window", false)
                + stat(Formats.count(figures.sampleCount()), "samples", false)
                + stat(String.valueOf(figures.eventTypeCount()), "event types", false)
                + stat(Formats.lossRatio(figures.lossRatio()), "sample loss", figures.lossRatio() > 0);
    }

    /**
     * An un-indexed dump has no figures to give. Saying so beats four zeroes, which read as a heap
     * that is empty rather than one that has not been looked at yet.
     */
    private static String heapFigures(RecordingState.HeapFigures figures) {
        if (figures == null) {
            return "";
        }
        if (!figures.cacheReady()) {
            return "<td colspan='4' class='sml'>" + NOT_INDEXED + "</td>";
        }
        return stat(Formats.bytes(figures.totalBytes()), "retained", false)
                + stat(Formats.count(figures.totalInstances()), "instances", false)
                + stat(Formats.count(figures.classCount()), "classes", false)
                + stat(Formats.count(figures.gcRootCount()), "GC roots", false);
    }

    private static String title(RecordingState state) {
        return switch (state.status()) {
            case NOT_IMPORTED, IMPORTED -> "JVM recording";
            case ANALYZING -> "Building the profile";
            case READY -> "Profile ready";
            case UNAVAILABLE -> "Microscope is not reachable";
        };
    }

    private static String subtitle(RecordingState state, Path file, String microscopeUrl) {
        return switch (state.status()) {
            case NOT_IMPORTED, IMPORTED -> "Not analysed yet";
            case ANALYZING -> "Parsing events and building views…";
            case READY -> profileName(state) + "  ·  " + Formats.bytes(state.sizeInBytes());
            case UNAVAILABLE -> microscopeUrl;
        };
    }

    private static String profileName(RecordingState state) {
        String name = state.summary() == null ? null : state.summary().profileName();
        return name == null || name.isBlank() ? state.filename() : name;
    }

    private static String stat(String value, String label, boolean alarming) {
        String cls = alarming ? "num alarm" : "num";
        return "<td width='25%'><span class='" + cls + "'>" + escape(value) + "</span><br>"
                + "<span class='sml'>" + escape(label) + "</span></td>";
    }

    private static String section(String name) {
        return "<table><tr><td class='hd'>" + escape(name) + "</td></tr></table><hr>";
    }

    private static String findings(RecordingState.ProfileSummary summary) {
        if (!summary.analysisComputed()) {
            return "<table><tr><td class='sml'>" + NOT_COMPUTED + " "
                    + link(ProfileView.AUTO_ANALYSIS.path(), "Run it in Microscope")
                    + "</td></tr></table>";
        }

        StringBuilder rows = new StringBuilder(256).append("<table>");
        for (RecordingState.Finding finding : summary.findings()) {
            String text = finding.summary() == null ? finding.rule() : finding.summary();
            rows.append("<tr><td class='").append(finding.isWarning() ? "warn" : "")
                    .append("'>").append(escape(text)).append("</td></tr>");
        }
        rows.append("<tr><td>").append(link(ProfileView.AUTO_ANALYSIS.path(), "Auto-analysis"))
                .append("</td></tr></table>");
        return rows.toString();
    }

    /**
     * The tile grid. A view the recording has no data for keeps its tile — dashed and unlinked —
     * because a missing tile teaches nothing, while a dimmed one says the recording lacks that data,
     * which is a fact about the run worth knowing.
     */
    private static String tiles(RecordingState.ProfileSummary summary) {
        return tiles(summary.views(), summary.disabledFeatures());
    }

    private static String tiles(List<ProfileView> views, List<String> disabledFeatures) {
        StringBuilder grid = new StringBuilder(1024)
                .append("<table cellspacing='6' class='tiles'>");

        for (int i = 0; i < views.size(); i++) {
            if (i % ProfileView.COLUMNS == 0) {
                grid.append("<tr>");
            }
            grid.append(tile(views.get(i), disabledFeatures));
            if (i % ProfileView.COLUMNS == ProfileView.COLUMNS - 1 || i == views.size() - 1) {
                grid.append("</tr>");
            }
        }
        return grid.append("</table>").toString();
    }

    private static String tile(ProfileView view, List<String> disabledFeatures) {
        String icon = "<icon src='" + view.iconKey() + "'/> ";

        if (!view.isAvailable(disabledFeatures)) {
            return "<td width='" + TILE_WIDTH + "' class='tile off'>"
                    + "<span class='off'>" + icon + "<b>" + escape(view.label()) + "</b><br>"
                    + "<span class='sml'>" + escape(view.unavailableBlurb()) + "</span></span></td>";
        }

        return "<td width='" + TILE_WIDTH + "' class='tile'>"
                + icon + link(view.path(), "<b>" + escape(view.label()) + "</b>") + "<br>"
                + "<span class='sml'>" + escape(view.blurb()) + "</span></td>";
    }

    private static String facts(RecordingState state, Path file, String microscopeUrl) {
        Path parent = file.getParent();
        return "<table>"
                + factRow("File", state.filename())
                + factRow("Size", Formats.bytes(state.sizeInBytes()))
                + factRow("Path", parent == null ? "—" : parent.toString())
                + factRow("Microscope", microscopeUrl)
                + "</table>";
    }

    private static String factRow(String key, String value) {
        return "<tr><td width='96' class='sml'>" + escape(key) + "</td>"
                + "<td class='mono'>" + escape(value) + "</td></tr>";
    }

    /**
     * The href is the view's own path, which is what lets one {@code HyperlinkListener} serve every
     * link on the panel instead of a listener per link.
     */
    private static String link(String path, String label) {
        return "<a href='" + escape(path) + "'>" + label + "</a>";
    }

    /**
     * Escapes text that reaches the markup from outside — file names, profile names and finding
     * summaries all come from somewhere this plugin does not control.
     */
    static String escape(String text) {
        return Html.escape(text);
    }
}
