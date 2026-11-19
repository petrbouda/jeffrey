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

package cafe.jeffrey.microscope.core.manager.ide;

/**
 * Bridges "open in IDE" and "fetch source" requests from the Microscope frontend to a
 * locally-running IDE plugin. The concrete implementation is selected by the
 * {@code jeffrey.microscope.ide.mode} property (see {@link IdeMode}):
 *
 * <ul>
 *   <li>{@link IdeMode#JEFFREY_PLUGIN} → {@link JeffreyPluginBridge}, the first-party Jeffrey IntelliJ
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
     * The active integration mode, surfaced to the frontend so it can vary behavior per bridge (e.g.
     * the flamegraph gates its IDE buttons on a per-class check only under {@link IdeMode#JFR_PROFILER_PLUGIN}).
     */
    IdeMode mode();

    /**
     * Whether the IDE contains {@code fqn} (class-level only). Used to enable/disable the flamegraph
     * IDE buttons per frame. The default assumes presence ({@code true}) so bridges that cannot answer
     * never gate the buttons; {@link JfrProfilerPluginBridge} overrides it with a real lookup.
     */
    default boolean hasClass(String profileId, String fqn) {
        return true;
    }

    /**
     * Opens a source location in the developer's IDE (navigate + focus the window).
     */
    IdeOpenResult open(IdeOpenRequest request);

    /**
     * Fetches the raw source text of a class for display inside Microscope.
     */
    IdeSourceResult fetchSource(IdeSourceRequest request);

    /**
     * Locates a source position without opening it.
     *
     * <p>Not every bridge can answer this: it needs an IDE that will resolve a symbol and report the
     * result rather than acting on it, which is a capability of the first-party plugin from protocol
     * version 2 onwards. The default says so in a sentence instead of pretending, because the caller
     * — typically an agent about to write down a file and a line — must not be handed a guess.
     */
    default IdeResolveResult resolve(IdeResolveRequest request) {
        return IdeResolveResult.failed(
                "This IDE integration cannot locate a frame without opening it.");
    }

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
