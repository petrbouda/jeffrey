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

package cafe.jeffrey.provisioner;

import cafe.jeffrey.provisioner.placeholder.JeffreyPlaceholderSource;
import cafe.jeffrey.provisioner.placeholder.Placeholders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;

/**
 * Provisions a Jeffrey session for one JVM: lays down its directories, declares it to the hub,
 * and writes the JVM options that arm the profiler.
 */
public class InitExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(InitExecutor.class);

    private final Clock clock;
    private final LayoutProvisioner layoutProvisioner;
    private final ProfilerSettingsResolver profilerSettingsResolver;
    private final OutputWriter outputWriter;

    public InitExecutor(Clock clock) {
        this.clock = clock;
        this.layoutProvisioner = new LayoutProvisioner();
        this.profilerSettingsResolver = new ProfilerSettingsResolver();
        this.outputWriter = new OutputWriter();
    }

    /**
     * @param config validated initialization configuration
     * @throws Exception if initialization fails
     */
    public void execute(InitConfig config) throws Exception {
        LOG.debug("Executing provisioner init: workspaceRefId={} projectName={}",
                config.getWorkspaceRefId(), config.getProjectName());

        ProjectLayout projectLayout = layoutProvisioner.provisionProject(config);

        SessionRegistrar registrar = new SessionRegistrar(
                new FileSystemRepository(clock, projectLayout.workspace()), layoutProvisioner);
        ProvisionedSession session = registrar.openSession(config, projectLayout);

        // Phase two of placeholder resolution: the layout only exists now that the session
        // directory has been created, so this is where <<JEFFREY:...>> becomes answerable.
        // Values carrying <<ENV:...>> were already resolved when the configuration was read.
        Placeholders placeholders = Placeholders.of(
                JeffreyPlaceholderSource.of(session.layout(), config.getProfilerPath(),
                        EnvFileBuilder.DEFAULT_FILE_TEMPLATE));

        String features = JvmFeatures.of(config, appIdentityFor(config, session))
                .render(session.layout().session(), placeholders);

        ProfilerSettingsResolver.ResolvedProfilerSettings resolvedSettings = profilerSettingsResolver.resolve(
                config.getProfilerConfig(),
                session.layout().workspace(),
                session.projectId(),
                config.getProjectName(),
                placeholders,
                features);

        registrar.recordSession(config, session, resolvedSettings);
        outputWriter.write(config, session.layout(), resolvedSettings.command());

        // Single greppable verdict line — the one place that tells a user their setup works
        LOG.info("Jeffrey profiling ENABLED: project={} workspace={} instance={} session={} profiler_source={} arg_file={}",
                config.getProjectName(), config.getWorkspaceRefId(), session.instanceId(), session.sessionId(),
                resolvedSettings.source(), config.getArgFilePath());
    }

    private AppIdentity appIdentityFor(InitConfig config, ProvisionedSession session) {
        return new AppIdentity(
                config.getWorkspaceRefId(),
                session.projectId(),
                config.getProjectName(),
                config.getProjectLabel(),
                session.instanceId(),
                session.sessionId(),
                session.order(),
                config.getAttributes(),
                clock.millis());
    }
}
