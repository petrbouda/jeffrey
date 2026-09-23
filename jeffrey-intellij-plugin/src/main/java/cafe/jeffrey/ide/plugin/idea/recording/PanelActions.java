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

package cafe.jeffrey.ide.plugin.idea.recording;

import cafe.jeffrey.ide.plugin.idea.agent.AgentCli;

import java.nio.file.Path;

/**
 * What the rendered panel can ask for.
 *
 * <p>An interface rather than a record of callbacks: these are eleven named things a reader of the
 * renderer needs to recognise, and {@code actions.analyze()} says more at the call site than the
 * fourth field of a constructor. {@link RecordingPanel} is the only implementation — it owns the
 * state machine, and the renderers own nothing but pixels.
 */
public interface PanelActions {

    /** Import the file into Microscope and build a profile from it. */
    void analyze();

    /** Ask Microscope about the file again, after it was unreachable or an analysis failed. */
    void retry();

    /** Poll while a profile is being built. */
    void checkAgain();

    /** Open the plugin's settings, where the Microscope address lives. */
    void openSettings();

    /** Open the profile's landing page in the browser. */
    void openProfile();

    /** Build a heap dump's index in Microscope, and keep the panel following it until it is done. */
    void buildIndex();

    /** Open one Microscope view, named by its sub-path under {@code /profiles/{id}/}. */
    void openView(String viewPath);

    /** Hand the profile to a coding agent, and remember it as the one to offer first next time. */
    void launchAgent(AgentCli agent);

    /**
     * Measure this recording against another file, importing it first when Microscope has not seen
     * it. The file opened here stays the primary: what is picked becomes the baseline.
     */
    void compareWith(Path baselineFile);

    /**
     * Exchange the primary and the baseline, by reopening the tab the other way round.
     *
     * <p>Its own action rather than a flag read further down, because direction is not a rendering
     * detail: every consumer of the pair — the differential link, the agent's prompt, the figures —
     * has to agree on it, and the only way to guarantee that is for there to be one place it is
     * decided.
     */
    void swapComparison();

    /** Drop the baseline and go back to a panel about one recording. */
    void clearComparison();
}
