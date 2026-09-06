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

    /** The default Microscope address — the port {@code run-microscope.sh} serves on. */
    public static final String DEFAULT_MICROSCOPE_URL = "http://localhost:8585";

    public static final class State {
        public boolean enabled = true;
        public String microscopeUrl = DEFAULT_MICROSCOPE_URL;
        public boolean agentsEnabled = true;
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
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    public void setMicroscopeUrl(String microscopeUrl) {
        state.microscopeUrl = microscopeUrl;
    }
}
