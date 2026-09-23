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

package cafe.jeffrey.ide.plugin.idea.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

/**
 * Persisted plugin settings.
 *
 * <p>{@code enabled} gates whether this IDE answers Microscope at all — while it is off every
 * endpoint returns {@code 404}, so a disabled IDE is invisible to Microscope's port scan rather than
 * visible and refusing.
 *
 * <p>{@code microscopeUrl} is only used in the other direction, by the action that sends a recording
 * to Microscope. Nothing discovers it: Microscope finds the IDE, not the reverse, so this is the one
 * address the plugin cannot work out for itself.
 */
@State(name = "JeffreySettings", storages = @Storage("jeffrey.xml"))
@Service(Service.Level.APP)
public final class JeffreySettings implements PersistentStateComponent<JeffreySettings.State> {

    private static final String PATH_SEPARATOR = "/";

    /** The default Microscope address — the port {@code run-microscope.sh} serves on. */
    public static final String DEFAULT_MICROSCOPE_URL = "http://localhost:8585";

    public static final class State {
        public boolean enabled = true;
        public String microscopeUrl = DEFAULT_MICROSCOPE_URL;
        public boolean agentsEnabled = true;
        public String preferredAgent = null;
    }

    private State state = new State();

    public static JeffreySettings getInstance() {
        return ApplicationManager.getApplication().getService(JeffreySettings.class);
    }

    @NotNull
    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public boolean isEnabled() {
        return state.enabled;
    }

    /**
     * Whether the recording panel offers to hand a profile to a coding agent.
     *
     * <p>Has a switch of its own because this is the one thing the plugin does that reaches outside
     * itself — it starts a process in the developer's shell. {@code hubs_} and {@code ide_} each got a
     * switch on the Microscope side for the same reason, and this is the same kind of reach.
     */
    public boolean areAgentsEnabled() {
        return state.agentsEnabled;
    }

    public void setAgentsEnabled(boolean agentsEnabled) {
        state.agentsEnabled = agentsEnabled;
    }

    /**
     * The executable of the agent launched last, or null before anything has been.
     *
     * <p>The recording panel's split button runs one agent directly, and this is what decides which.
     * Without it the choice would fall to whichever entry {@code AgentCli.ALL} declares first, which
     * is invisible from the button and would move under an unrelated edit to that list.
     */
    public String preferredAgent() {
        return state.preferredAgent;
    }

    public void setPreferredAgent(String preferredAgent) {
        state.preferredAgent = preferredAgent;
    }

    public void setEnabled(boolean enabled) {
        state.enabled = enabled;
    }

    /**
     * The Microscope address, without a trailing slash. Falls back to the default rather than
     * returning blank: a cleared field should send the developer to the usual address, not build a
     * URL that cannot resolve.
     */
    public String microscopeUrl() {
        String url = state.microscopeUrl;
        if (url == null || url.isBlank()) {
            return DEFAULT_MICROSCOPE_URL;
        }
        String trimmed = url.trim();
        return trimmed.endsWith(PATH_SEPARATOR) ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    public void setMicroscopeUrl(String microscopeUrl) {
        state.microscopeUrl = microscopeUrl;
    }
}
