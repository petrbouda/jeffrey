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

package cafe.jeffrey.microscope.core.manager.ide;

/**
 * Bridges "open in IDE" and "fetch source" requests from the Microscope frontend to a
 * locally-running IDE plugin. The concrete implementation is selected by the
 * {@code jeffrey.microscope.ide.mode} property (see {@link IdeMode}):
 *
 * <ul>
 *   <li>{@link IdeMode#DEFAULT} → {@link JeffreyPluginBridge}, the first-party Jeffrey IntelliJ
 *       plugin (instance discovery, per-profile target selection, precise PSI navigation).</li>
 *   <li>{@link IdeMode#JFR_PROFILER_PLUGIN} → {@link JfrProfilerPluginBridge}, which targets the
 *       third-party JFR Profiler IntelliJ plugin via its {@code /ide/{fqn}.{method}} HTTP contract.</li>
 * </ul>
 *
 * <p>All operations are best-effort: an offline, unconfigured, or rejecting IDE yields a failed
 * result with a human-readable message rather than an exception — the IDE being unavailable is an
 * expected condition.
 */
public interface IdeBridge {

    /**
     * Whether IDE integration is enabled at all. Gates the frontend "Open in IDE" / "View Source"
     * affordances regardless of the selected {@link IdeMode}. Both real bridges return {@code true};
     * the feature is always available (it shows onboarding until a window is linked).
     */
    boolean isEnabled();

    /**
     * Opens a source location in the developer's IDE (navigate + focus the window).
     */
    IdeOpenResult open(IdeOpenRequest request);

    /**
     * Fetches the raw source text of a class for display inside Microscope.
     */
    IdeSourceResult fetchSource(IdeSourceRequest request);

    /**
     * Discovers the IDE windows available as navigation targets for a profile, flagging which contain
     * {@code fqn}, and reporting the profile's currently cached choice. Only meaningful for the
     * {@code default} bridge (multi-window); the single-URL JFR Profiler bridge returns an empty result.
     */
    default IdeTargetsResult discoverTargets(String profileId, String fqn) {
        return IdeTargetsResult.empty();
    }

    /**
     * Records the chosen window for a profile (cached and reused for later jumps). Returns {@code true}
     * if accepted. No-op for the single-URL JFR Profiler bridge.
     */
    default boolean selectTarget(String profileId, IdeTarget target) {
        return false;
    }

    /**
     * The cached IDE link for a profile, read without any discovery / port scan. Only the multi-window
     * {@code default} bridge is {@code selectable}; other bridges report {@link IdeTargetStatus#notSelectable()}
     * so the profile-wide nav control stays hidden.
     */
    default IdeTargetStatus targetStatus(String profileId) {
        return IdeTargetStatus.notSelectable();
    }

    /**
     * Clears the cached window for a profile (disconnect). Returns {@code true} if a link was removed.
     * No-op for the single-URL JFR Profiler bridge.
     */
    default boolean clearTarget(String profileId) {
        return false;
    }
}
