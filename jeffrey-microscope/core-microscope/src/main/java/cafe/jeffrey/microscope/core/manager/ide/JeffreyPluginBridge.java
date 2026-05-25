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

import cafe.jeffrey.microscope.core.manager.ide.IdeTargetsResult.IdeInstanceView;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetsResult.IdeProjectView;
import cafe.jeffrey.microscope.core.manager.ide.JeffreyPluginClient.NavigateBody;
import cafe.jeffrey.microscope.core.manager.ide.JeffreyPluginClient.PluginInstance;
import cafe.jeffrey.microscope.core.manager.ide.JeffreyPluginClient.PluginNavigateResult;
import cafe.jeffrey.microscope.core.manager.ide.JeffreyPluginClient.PluginProject;
import cafe.jeffrey.microscope.core.manager.ide.JeffreyPluginClient.PluginSourceResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link IdeBridge} for {@link IdeMode#DEFAULT}: talks to the first-party Jeffrey IntelliJ plugin.
 * Discovers IDE instances by scanning the built-in-server port range (no token, localhost only),
 * remembers the chosen window per profile ({@link IdeTargetCache}), and navigates / fetches source
 * via {@link JeffreyPluginClient}.
 *
 * <p>Discovery is deliberately lazy: {@link #targetStatus(String)} reads the cache only (no scan), so
 * a linked window stays linked. A jump tries the cached port directly; only when that is unreachable
 * do we run a single discovery to re-resolve the project to its (possibly new) port and retry — this
 * keeps working across IDE restarts without scanning on every profile view.
 */
public final class JeffreyPluginBridge implements IdeBridge {

    private static final Logger LOG = LoggerFactory.getLogger(JeffreyPluginBridge.class);

    private static final String MSG_NO_TARGET = "No IDE window selected for this profile";
    private static final String MSG_TARGET_GONE = "The selected IDE window is no longer open";
    private static final String MSG_NOT_RESOLVED = "The IDE could not resolve this location";
    private static final String MSG_SOURCE_UNAVAILABLE = "Source is not available for this class";

    private final PortRange portRange;
    private final JeffreyPluginClient client;
    private final IdeTargetCache cache;

    public JeffreyPluginBridge(PortRange portRange, JeffreyPluginClient client, IdeTargetCache cache) {
        this.portRange = portRange;
        this.client = client;
        this.cache = cache;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public IdeOpenResult open(IdeOpenRequest request) {
        IdeTarget cached = cache.get(request.profileId());
        if (cached == null) {
            return IdeOpenResult.failed(MSG_NO_TARGET, IdeFailureReason.NO_TARGET);
        }

        PluginNavigateResult result = client.navigate(cached.port(), navBody(cached, request));
        if (result != null) {
            return openResult(result);
        }

        // Unreachable on the cached port — the IDE may have restarted on a new port. Re-resolve once.
        IdeTarget live = reresolve(request.profileId(), cached);
        if (live != null) {
            PluginNavigateResult retry = client.navigate(live.port(), navBody(live, request));
            if (retry != null) {
                return openResult(retry);
            }
        }
        return IdeOpenResult.failed(MSG_TARGET_GONE, IdeFailureReason.UNREACHABLE);
    }

    @Override
    public IdeSourceResult fetchSource(IdeSourceRequest request) {
        IdeTarget cached = cache.get(request.profileId());
        if (cached == null) {
            return IdeSourceResult.failed(MSG_NO_TARGET);
        }

        PluginSourceResult result = client.source(cached.port(), cached.projectId(), request.fqn());
        if (result != null) {
            return sourceResult(result);
        }

        IdeTarget live = reresolve(request.profileId(), cached);
        if (live != null) {
            PluginSourceResult retry = client.source(live.port(), live.projectId(), request.fqn());
            if (retry != null) {
                return sourceResult(retry);
            }
        }
        return IdeSourceResult.failed(MSG_TARGET_GONE);
    }

    @Override
    public IdeTargetsResult discoverTargets(String profileId, String fqn) {
        List<IdeInstanceView> instances = new ArrayList<>();
        for (PluginInstance instance : discover()) {
            instances.add(toInstanceView(instance, fqn));
        }
        IdeTarget cached = cache.get(profileId);
        return new IdeTargetsResult(cached == null ? null : cached.projectId(), instances);
    }

    @Override
    public boolean selectTarget(String profileId, IdeTarget target) {
        if (profileId == null || target == null || target.projectId() == null) {
            return false;
        }
        cache.put(profileId, target);
        return true;
    }

    @Override
    public IdeTargetStatus targetStatus(String profileId) {
        IdeTarget cached = cache.get(profileId);
        return cached == null ? IdeTargetStatus.notLinked() : IdeTargetStatus.linked(cached);
    }

    @Override
    public boolean clearTarget(String profileId) {
        if (profileId == null || cache.get(profileId) == null) {
            return false;
        }
        cache.clear(profileId);
        return true;
    }

    /**
     * Re-resolves a cached project to whichever live instance currently hosts it (the IDE may have
     * restarted on a new port). Matches by {@code projectId} (the stable locationHash) first, then by
     * {@code projectName}. Refreshes the cache with the live port/pid. Null when found nowhere.
     */
    private IdeTarget reresolve(String profileId, IdeTarget cached) {
        List<PluginInstance> instances = discover();
        IdeTarget live = matchInstances(instances, cached, true);
        if (live == null) {
            live = matchInstances(instances, cached, false);
        }
        if (live != null) {
            cache.put(profileId, live);
        }
        return live;
    }

    private static IdeTarget matchInstances(List<PluginInstance> instances, IdeTarget cached, boolean byProjectId) {
        for (PluginInstance instance : instances) {
            for (PluginProject project : instance.projects()) {
                boolean match = byProjectId
                        ? cached.projectId().equals(project.id())
                        : cached.projectName() != null && cached.projectName().equals(project.name());
                if (match) {
                    return new IdeTarget(
                            instance.port(), project.id(), instance.ideName(), project.name(), instance.pid());
                }
            }
        }
        return null;
    }

    private static NavigateBody navBody(IdeTarget target, IdeOpenRequest request) {
        return new NavigateBody(
                target.projectId(), request.fqn(), simpleMethod(request.method()), request.line(), null);
    }

    private static IdeOpenResult openResult(PluginNavigateResult result) {
        if (result.resolved()) {
            return IdeOpenResult.succeeded();
        }
        return IdeOpenResult.failed(reasonOr(result.reason(), MSG_NOT_RESOLVED), IdeFailureReason.NOT_RESOLVED);
    }

    private static IdeSourceResult sourceResult(PluginSourceResult result) {
        if (!result.resolved() || result.content() == null || result.content().isBlank()) {
            return IdeSourceResult.failed(reasonOr(result.reason(), MSG_SOURCE_UNAVAILABLE));
        }
        return IdeSourceResult.succeeded(result.content(), result.decompiled());
    }

    private List<PluginInstance> discover() {
        List<PluginInstance> instances = new ArrayList<>();
        for (int port = portRange.start(); port <= portRange.end(); port++) {
            client.instance(port).ifPresent(instances::add);
        }
        LOG.debug("IDE discovery scanned ports: range={} found={}", portRange, instances.size());
        return instances;
    }

    private IdeInstanceView toInstanceView(PluginInstance instance, String fqn) {
        List<IdeProjectView> projects = new ArrayList<>();
        for (PluginProject project : instance.projects()) {
            boolean hasClass = fqn != null && !fqn.isBlank() && client.hasClass(instance.port(), project.id(), fqn);
            projects.add(new IdeProjectView(
                    project.id(), project.name(), project.basePath(), project.vcsBranch(), project.focused(), hasClass));
        }
        return new IdeInstanceView(
                instance.port(), instance.ideName(), instance.ideVersion(), instance.pid(), projects);
    }

    /** The plugin matches by simple method name; strip any {@code ClassName.} prefix. */
    private static String simpleMethod(String method) {
        if (method == null) {
            return null;
        }
        int dot = method.lastIndexOf('.');
        return dot >= 0 ? method.substring(dot + 1) : method;
    }

    private static String reasonOr(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason;
    }
}
