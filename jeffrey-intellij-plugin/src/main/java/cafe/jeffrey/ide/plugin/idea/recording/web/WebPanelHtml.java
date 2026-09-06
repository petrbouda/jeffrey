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
import cafe.jeffrey.ide.plugin.idea.recording.Formats;
import cafe.jeffrey.ide.plugin.idea.recording.Html;
import cafe.jeffrey.ide.plugin.idea.recording.ProfileView;
import cafe.jeffrey.ide.plugin.idea.recording.RecordingState;

import java.nio.file.Path;
import java.util.List;

/**
 * The panel as one web document.
 *
 * <p>Pure string building with no Swing and no JCEF in sight, for the same reason the Swing renderer
 * keeps {@code PanelHtml} separate: the part that is easy to get wrong — which tile is dimmed, what a
 * heap dump shows instead of a recording window, whether a filename with an ampersand escapes — is
 * then testable without an IDE fixture, and {@link CefPanelRenderer} decides nothing.
 *
 * <p><b>Everything clickable is a button carrying a {@code data-action}.</b> No {@code href} appears
 * anywhere, which is what makes it impossible for a stray click to navigate the panel away from
 * itself — the alternative was a {@code CefRequestHandler} vetoing navigations after the fact. One
 * delegated listener reads the action off the nearest ancestor and hands the string to Java, the same
 * economy the Swing renderer gets from a single {@code HyperlinkListener}.
 */
public final class WebPanelHtml {

    /** How the page calls back into Java. Defined by the bridge script the renderer supplies. */
    private static final String SEND = "window.__jeffrey";

    private static final String NOT_INDEXED =
            "This heap dump has not been indexed yet, so it has no figures to show. Open it in "
            + "Microscope to build the index — the views below cannot answer anything until it exists.";

    private static final String ANALYZE_EXPLAINS =
            "Analysing copies the recording into Microscope, parses its events and builds the views. "
            + "It leaves the file on disk untouched.";

    private static final String ANALYZING_EXPLAINS =
            "This runs in Microscope, not in the IDE — closing this tab will not stop it.";

    private static final String UNREACHABLE_EXPLAINS =
            "Start Jeffrey Microscope, or point the plugin at the address it is actually serving on.";

    private static final String NOT_COMPUTED = "Not computed for this profile yet.";

    private static final String AGENTS_FOOT =
            "Install any of these and it turns on by itself.";

    /**
     * What the document needs to draw itself.
     *
     * @param agentsEnabled the {@code Settings → Tools → Jeffrey Plugin} switch. When off the
     *                      split button is absent entirely rather than disabled — a disabled control
     *                      invites a developer to hunt for what would enable it, and the answer here
     *                      is a setting they turned off themselves
     */
    public record Content(
            RecordingState state,
            Path file,
            String microscopeUrl,
            AgentRow agents,
            boolean agentsEnabled) {
    }

    private WebPanelHtml() {
    }

    public static String document(Content content, String bridgeScript) {
        return page(body(content), bridgeScript);
    }

    /** Drawn while the first request to Microscope is still in flight. */
    public static String loading(String bridgeScript) {
        return page("<div class='body'><p class='note last'>Asking Microscope about this file…</p></div>",
                bridgeScript);
    }

    /** Drawn when asking Microscope to analyse the file threw rather than answering. */
    public static String failure(String message, String filename, long sizeInBytes, String bridgeScript) {
        String body = accent(true)
                + header("offline", true, plainTitle("The analysis did not finish"),
                        Html.escape(filename) + " · " + Formats.bytes(sizeInBytes),
                        buttons(button("retry", "Try again", false, false), settingsButton()))
                + "<div class='body' style='padding-top:calc(20*var(--u))'>"
                + "<div class='err'>" + PanelSvg.icon("warn")
                + "<div><div class='t'>Microscope could not build a profile</div>"
                + "<div class='m'>" + Html.escape(message) + "</div></div></div>"
                + "<p class='note last'>The file may be truncated, still being written, or in a form "
                + "Microscope does not recognise.</p></div>";
        return page(body, bridgeScript);
    }

    // --- the document ---------------------------------------------------------------------------

    private static String page(String body, String bridgeScript) {
        return "<!doctype html><html><head><meta charset='utf-8'>"
                + "<style>" + WebPanelStyles.sheet() + "</style></head><body>"
                + body
                + "<script>" + bridgeScript + SCRIPT + "</script>"
                + "</body></html>";
    }

    private static String body(Content content) {
        RecordingState state = content.state();
        return switch (state.status()) {
            case READY -> ready(content);
            case NOT_IMPORTED, IMPORTED -> notAnalysed(content);
            case ANALYZING -> analyzing();
            case UNAVAILABLE -> unavailable(content);
        };
    }

    // --- ready ----------------------------------------------------------------------------------

    private static String ready(Content content) {
        RecordingState.ProfileSummary summary = content.state().summary();
        if (summary == null) {
            // READY without a summary should not happen, but a blank tab is the worst possible way to
            // find that out. Fall back to the facts, which are true whatever Microscope answered.
            return notAnalysed(content);
        }

        String subtitle = (summary.isHeapDump() ? "Heap dump" : "JFR recording")
                + " · " + Formats.bytes(content.state().sizeInBytes())
                + " · profile ready";

        StringBuilder html = new StringBuilder(4096);
        html.append(accent(false))
                .append(header("flame", false,
                        "<div class='fname'>" + Html.escape(profileName(content.state())) + "</div>",
                        subtitle,
                        readyActions(content)));

        String figures = figures(summary);
        if (!figures.isEmpty()) {
            html.append("<div class='figs'>").append(figures).append("</div>");
        }

        html.append("<div class='body'>");
        // Only a dump explains itself here. A recording that reported no figures is a different and
        // rarer thing, and telling its reader about a heap index would be a lie with a straight face.
        if (figures.isEmpty() && summary.isHeapDump()) {
            html.append("<p class='note'>").append(NOT_INDEXED).append("</p>");
        }
        // A heap dump gets no findings section at all rather than an empty one: its verdict is Leak
        // suspects, which already leads its tile grid.
        boolean ruled = false;
        if (!summary.isHeapDump()) {
            html.append(findings(summary));
            ruled = true;
        }
        html.append(views(summary, ruled)).append("</div>");
        return html.toString();
    }

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
        double loss = figures.lossRatio();
        return figure(Formats.duration(figures.durationInMillis()), "window", false, -1)
                + figure(Formats.count(figures.sampleCount()), "samples", false, -1)
                + figure(String.valueOf(figures.eventTypeCount()), "event types", false, -1)
                + figure(Formats.lossRatio(loss), "sample loss", loss > 0, loss);
    }

    private static String heapFigures(RecordingState.HeapFigures figures) {
        if (figures == null || !figures.cacheReady()) {
            return "";
        }
        return figure(Formats.bytes(figures.totalBytes()), "retained", false, -1)
                + figure(Formats.count(figures.totalInstances()), "instances", false, -1)
                + figure(Formats.count(figures.classCount()), "classes", false, -1)
                + figure(Formats.count(figures.gcRootCount()), "GC roots", false, -1);
    }

    /**
     * One figure tile. Sample loss carries a meter because a percentage on its own gives no sense of
     * scale, and it is the one figure that can invalidate every other number on the panel.
     *
     * @param ratio 0..1 to draw a meter under the value, or negative for no meter
     */
    private static String figure(String value, String label, boolean alarming, double ratio) {
        String meter = "";
        if (ratio >= 0) {
            long percent = Math.min(100, Math.round(ratio * 100));
            meter = "<div class='meter'><i style='width:" + percent + "%'></i></div>";
        }
        return "<div class='fig" + (alarming ? " bad" : "") + "'>"
                + "<div class='v tnum'>" + Html.escape(value) + "</div>"
                + "<div class='k'>" + Html.escape(label) + "</div>"
                + meter + "</div>";
    }

    /**
     * Findings, each titled by the rule that fired.
     *
     * <p>The rule name is the specific thing JMC matched — {@code GC Pauses}, {@code Thrown Errors} —
     * and the summary is what it measured. Titling by the rule is what turns five sentences into five
     * findings a reader can tell apart at a glance, and it costs nothing: {@code rule} already
     * arrives on the wire.
     */
    private static String findings(RecordingState.ProfileSummary summary) {
        StringBuilder html = new StringBuilder(1024).append("<div class='aa-head'><span class='sect'>Auto-analysis</span>");

        if (!summary.analysisComputed()) {
            // The heading survives with the count dropped. A section that vanishes entirely reads as a
            // rendering bug rather than as missing data.
            return html.append("</div><p class='aa-none'>").append(NOT_COMPUTED).append(" ")
                    .append(viewLink(ProfileView.AUTO_ANALYSIS.path(), "Run it in Microscope"))
                    .append("</p>").toString();
        }

        List<RecordingState.Finding> findings = summary.findings();
        html.append("<span class='cnt'>").append(findings.size())
                .append(findings.size() == 1 ? " finding" : " findings").append("</span></div>");

        for (RecordingState.Finding finding : findings) {
            String title = finding.rule() == null || finding.rule().isBlank()
                    ? "Finding"
                    : finding.rule();
            String detail = finding.summary() == null ? "" : finding.summary();
            html.append("<div class='find'>").append(PanelSvg.icon("warn"))
                    .append("<div><div class='rn'>").append(Html.escape(title)).append("</div>")
                    .append("<div class='sm'>").append(Html.escape(detail)).append("</div></div></div>");
        }
        return html.append(viewLink(ProfileView.AUTO_ANALYSIS.path(),
                "Open auto-analysis in Microscope", "link aa-more")).toString();
    }

    private static String views(RecordingState.ProfileSummary summary, boolean ruled) {
        StringBuilder html = new StringBuilder(2048);
        if (ruled) {
            html.append("<div class='rule'></div>");
        }
        html.append("<span class='sect'>Open a view</span><div class='views'>");
        for (ProfileView view : summary.views()) {
            html.append(card(view, summary.disabledFeatures()));
        }
        return html.append("</div>").toString();
    }

    private static String card(ProfileView view, List<String> disabledFeatures) {
        String icon = "<div class='iw'>" + PanelSvg.icon(view.iconKey()) + "</div>";
        String label = "<div><div class='l'>" + Html.escape(view.label()) + "</div>";

        if (!view.isAvailable(disabledFeatures)) {
            return "<div class='card off'>" + icon + label
                    + "<div class='b'>" + Html.escape(view.unavailableBlurb()) + "</div></div></div>";
        }
        return "<button type='button' class='card' data-action='view:" + Html.escape(view.path()) + "'>"
                + icon + label + "<div class='b'>" + Html.escape(view.blurb()) + "</div></div></button>";
    }

    // --- the other states -----------------------------------------------------------------------

    private static String notAnalysed(Content content) {
        return accent(false)
                + header("flame", false, plainTitle("Not analysed yet"),
                        "Microscope has not seen this file",
                        buttons(button("analyze", "Analyze in Microscope", true, false)))
                + "<div class='body' style='padding-top:calc(20*var(--u))'>"
                + "<p class='note'>" + ANALYZE_EXPLAINS + "</p>"
                + facts(content, true)
                + "</div>";
    }

    private static String analyzing() {
        return accent(false)
                + header("flame", false, plainTitle("Building the profile"),
                        "Parsing events and building views…",
                        buttons(button("check", "Check again", false, false)))
                + "<div class='body' style='padding-top:calc(20*var(--u))'>"
                + "<div class='prog'><i></i></div>"
                + "<p class='note last'>" + ANALYZING_EXPLAINS + "</p></div>";
    }

    /**
     * Microscope did not answer. The accent bar and the file icon go neutral, so the panel reads as
     * wrong before a word of it does — the flame is reserved for a panel that actually knows
     * something. Settings appears here and on a failure only, the two states where a wrong address is
     * the likely fix.
     */
    private static String unavailable(Content content) {
        return accent(true)
                + header("offline", true, plainTitle("Microscope is not reachable"),
                        "Nothing is known about this file until it answers",
                        buttons(button("retry", "Try again", false, false), settingsButton()))
                + "<div class='body' style='padding-top:calc(20*var(--u))'>"
                + "<p class='note'>" + UNREACHABLE_EXPLAINS + "</p>"
                + facts(content, false)
                + "</div>";
    }

    private static String facts(Content content, boolean withPath) {
        Path parent = content.file().getParent();
        StringBuilder html = new StringBuilder(512).append("<dl class='facts'>")
                .append(fact("File", content.state().filename()))
                .append(fact("Size", Formats.bytes(content.state().sizeInBytes())));
        if (withPath) {
            html.append(fact("Path", parent == null ? "—" : parent.toString()));
        }
        return html.append(fact("Microscope", content.microscopeUrl())).append("</dl>").toString();
    }

    private static String fact(String key, String value) {
        return "<dt>" + Html.escape(key) + "</dt><dd>" + Html.escape(value) + "</dd>";
    }

    // --- the action row -------------------------------------------------------------------------

    private static String readyActions(Content content) {
        String open = button("open", "Open in Microscope", true, false);
        if (!content.agentsEnabled()) {
            return buttons(open);
        }
        return buttons(open + agentSplitButton(content.agents()));
    }

    /**
     * The agents, as one split button.
     *
     * <p>A button each is what this used to be, and it grew with {@code AgentCli.ALL} while spending
     * width on agents that could not be pressed. Here the primary half runs the agent that ran last,
     * the chevron holds the rest, and the row is the same width whether Jeffrey knows two agents or
     * eight.
     */
    private static String agentSplitButton(AgentRow agents) {
        List<AgentCli> ready = agents.ready();
        List<AgentCli> missing = agents.missing();

        String main;
        if (agents.hasInstalled()) {
            AgentCli primary = agents.primary();
            main = "<button type='button' class='btn main' data-action='agent:"
                    + Html.escape(primary.executable()) + "'>"
                    + mark(primary, true) + "Analyse with " + Html.escape(primary.displayName())
                    + "</button>";
        } else {
            main = "<button type='button' class='btn main' disabled>Analyse with an agent</button>";
        }

        StringBuilder menu = new StringBuilder(768).append("<div class='pop' hidden>");
        if (!ready.isEmpty()) {
            menu.append("<div class='grp'>Ready</div>");
            for (AgentCli agent : ready) {
                menu.append("<button type='button' class='mi' data-action='agent:")
                        .append(Html.escape(agent.executable())).append("'>")
                        .append(mark(agent, true))
                        .append("<span class='n'>Analyse with ").append(Html.escape(agent.displayName()))
                        .append("</span></button>");
            }
        }
        if (!missing.isEmpty()) {
            if (!ready.isEmpty()) {
                menu.append("<div class='divider'></div>");
            }
            menu.append("<div class='grp'>Not on PATH</div>");
            for (AgentCli agent : missing) {
                menu.append("<div class='mi dis'>").append(mark(agent, false))
                        .append("<span class='n'>").append(Html.escape(agent.displayName()))
                        .append("</span><span class='tail'>").append(Html.escape(agent.executable()))
                        .append("</span></div>");
            }
            menu.append("<div class='foot'>").append(AGENTS_FOOT).append("</div>");
        }
        menu.append("</div>");

        return "<div class='anchor'><div class='split'>" + main
                + "<button type='button' class='btn arrow' data-menu aria-haspopup='true'"
                + " aria-expanded='false' aria-label='More agents'>"
                + PanelSvg.icon("chevron", "chev") + "</button></div>"
                + menu + "</div>";
    }

    private static String mark(AgentCli agent, boolean installed) {
        return "<span class='mark" + (installed ? "" : " grey") + "'>"
                + Html.escape(agent.mark()) + "</span>";
    }

    // --- small pieces ---------------------------------------------------------------------------

    private static String accent(boolean mute) {
        return "<div class='accent" + (mute ? " mute" : "") + "'></div>";
    }

    private static String header(String iconKey, boolean mute, String title, String subtitle, String actions) {
        return "<div class='hdr'><div class='well" + (mute ? " mute" : "") + "'>"
                + PanelSvg.icon(iconKey, "ico ico-lg") + "</div>"
                + "<div>" + title + "<div class='sub'>" + Html.escape(subtitle) + "</div></div>"
                + actions + "</div>";
    }

    private static String plainTitle(String text) {
        return "<div class='title'>" + Html.escape(text) + "</div>";
    }

    private static String buttons(String... controls) {
        return "<div class='actions'>" + String.join("", controls) + "</div>";
    }

    private static String button(String action, String label, boolean flame, boolean disabled) {
        return "<button type='button' class='btn" + (flame ? " flame" : "") + "'"
                + (disabled ? " disabled" : " data-action='" + Html.escape(action) + "'")
                + ">" + Html.escape(label) + "</button>";
    }

    private static String settingsButton() {
        return button("settings", "Settings…", false, false);
    }

    private static String viewLink(String path, String label) {
        return viewLink(path, label, "link");
    }

    private static String viewLink(String path, String label, String cssClass) {
        return "<button type='button' class='" + cssClass + "' data-action='view:"
                + Html.escape(path) + "'>" + Html.escape(label) + "</button>";
    }

    private static String profileName(RecordingState state) {
        String name = state.summary() == null ? null : state.summary().profileName();
        return name == null || name.isBlank() ? state.filename() : name;
    }

    /**
     * One delegated click listener for the whole document, plus the split button's menu.
     *
     * <p>The menu opens and closes entirely in the page — a round trip to Java to show a popup would
     * be a round trip the developer can feel.
     */
    private static final String SCRIPT = """
            (function(){
              var pop = document.querySelector('.pop');
              var arrow = document.querySelector('[data-menu]');
              function close(){
                if(!pop){ return; }
                pop.hidden = true;
                arrow.setAttribute('aria-expanded','false');
              }
              document.addEventListener('click', function(event){
                var toggle = event.target.closest('[data-menu]');
                if(toggle && pop){
                  var open = pop.hidden;
                  pop.hidden = !open;
                  arrow.setAttribute('aria-expanded', String(open));
                  return;
                }
                var target = event.target.closest('[data-action]');
                if(!target){
                  close();
                  return;
                }
                close();
                __SEND__(target.getAttribute('data-action'));
              });
              document.addEventListener('keydown', function(event){
                if(event.key === 'Escape'){ close(); }
              });
            })();
            """.replace("__SEND__", SEND);
}
