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
 * via {@link JeffreyPluginClient}. The window is picked once per profile (by Microscope's UI) and
 * reused; the volatile port is re-resolved from the cached project on each call so an IDE restart
 * keeps working.
 */
public final class JeffreyPluginBridge implements IdeBridge {

    private static final Logger LOG = LoggerFactory.getLogger(JeffreyPluginBridge.class);

    private static final String MSG_NO_TARGET = "No IDE window selected for this profile";
    private static final String MSG_TARGET_GONE = "The selected IDE window is no longer open";
    private static final String MSG_UNREACHABLE = "Could not reach the IDE plugin — is it running?";
    private static final String MSG_NOT_RESOLVED = "The IDE could not resolve this location";
    private static final String MSG_SOURCE_UNAVAILABLE = "Source is not available for this class";

    private final boolean enabled;
    private final int portStart;
    private final int portEnd;
    private final JeffreyPluginClient client;
    private final IdeTargetCache cache;

    public JeffreyPluginBridge(boolean enabled, int portStart, int portEnd,
                               JeffreyPluginClient client, IdeTargetCache cache) {
        this.enabled = enabled;
        this.portStart = portStart;
        this.portEnd = portEnd;
        this.client = client;
        this.cache = cache;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public IdeOpenResult open(IdeOpenRequest request) {
        if (!enabled) {
            return IdeOpenResult.failed("IDE integration is disabled");
        }
        IdeTarget target = resolveTarget(request.profileId());
        if (target == null) {
            return IdeOpenResult.failed(cache.get(request.profileId()) == null ? MSG_NO_TARGET : MSG_TARGET_GONE);
        }

        NavigateBody body = new NavigateBody(
                target.projectId(), request.fqn(), simpleMethod(request.method()), request.line(), null);
        PluginNavigateResult result = client.navigate(target.port(), body);
        if (result == null) {
            return IdeOpenResult.failed(MSG_UNREACHABLE);
        }
        if (!result.resolved()) {
            return IdeOpenResult.failed(reasonOr(result.reason(), MSG_NOT_RESOLVED));
        }
        return IdeOpenResult.succeeded();
    }

    @Override
    public IdeSourceResult fetchSource(IdeSourceRequest request) {
        if (!enabled) {
            return IdeSourceResult.failed("IDE integration is disabled");
        }
        IdeTarget target = resolveTarget(request.profileId());
        if (target == null) {
            return IdeSourceResult.failed(cache.get(request.profileId()) == null ? MSG_NO_TARGET : MSG_TARGET_GONE);
        }

        PluginSourceResult result = client.source(target.port(), target.projectId(), request.fqn());
        if (result == null) {
            return IdeSourceResult.failed(MSG_UNREACHABLE);
        }
        if (!result.resolved() || result.content() == null || result.content().isBlank()) {
            return IdeSourceResult.failed(reasonOr(result.reason(), MSG_SOURCE_UNAVAILABLE));
        }
        return IdeSourceResult.succeeded(result.content());
    }

    @Override
    public IdeTargetsResult discoverTargets(String profileId, String fqn) {
        if (!enabled) {
            return IdeTargetsResult.empty();
        }
        List<IdeInstanceView> instances = new ArrayList<>();
        for (PluginInstance instance : discover()) {
            instances.add(toInstanceView(instance, fqn));
        }
        IdeTarget cached = cache.get(profileId);
        return new IdeTargetsResult(cached == null ? null : cached.projectId(), instances);
    }

    @Override
    public boolean selectTarget(String profileId, int port, String projectId) {
        if (!enabled || profileId == null || projectId == null) {
            return false;
        }
        cache.put(profileId, new IdeTarget(port, projectId));
        return true;
    }

    /**
     * Returns the live target for a profile: the cached project re-resolved to whichever instance
     * currently hosts it (so an IDE restart on a new port still works). Null when nothing is cached or
     * the cached project is no longer open anywhere.
     */
    private IdeTarget resolveTarget(String profileId) {
        IdeTarget cached = cache.get(profileId);
        if (cached == null) {
            return null;
        }
        for (PluginInstance instance : discover()) {
            for (PluginProject project : instance.projects()) {
                if (cached.projectId().equals(project.id())) {
                    IdeTarget live = new IdeTarget(instance.port(), cached.projectId());
                    cache.put(profileId, live);
                    return live;
                }
            }
        }
        return null;
    }

    private List<PluginInstance> discover() {
        List<PluginInstance> instances = new ArrayList<>();
        for (int port = portStart; port <= portEnd; port++) {
            client.instance(port).ifPresent(instances::add);
        }
        LOG.debug("IDE discovery scanned ports: range={}-{} found={}", portStart, portEnd, instances.size());
        return instances;
    }

    private IdeInstanceView toInstanceView(PluginInstance instance, String fqn) {
        List<IdeProjectView> projects = new ArrayList<>();
        for (PluginProject project : instance.projects()) {
            boolean hasClass = fqn != null && client.hasClass(instance.port(), project.id(), fqn);
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
