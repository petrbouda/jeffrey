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

import cafe.jeffrey.provisioner.config.VolumeConfigLayer;
import cafe.jeffrey.provisioner.config.VolumeConfigLayers;
import cafe.jeffrey.provisioner.placeholder.JeffreyPlaceholderSource;
import cafe.jeffrey.provisioner.placeholder.Placeholders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;

/**
 * Provisions a Jeffrey session for one JVM: lays down its directories, declares it to the hub,
 * and writes the JVM options that arm the profiler.
 */
public class InitExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(InitExecutor.class);

    private final Clock clock;
    private final LayoutProvisioner layoutProvisioner;
    private final ProfilerCommandResolver profilerCommandResolver;
    private final OutputWriter outputWriter;

    public InitExecutor(Clock clock) {
        this.clock = clock;
        this.layoutProvisioner = new LayoutProvisioner();
        this.profilerCommandResolver = new ProfilerCommandResolver();
        this.outputWriter = new OutputWriter();
    }

    /**
     * @param initialConfig validated configuration, as read from the container's own layers; the
     *                      hub-published layers are merged in once the layout names their folders
     * @throws Exception if initialization fails
     */
    public void execute(InitConfig initialConfig) throws Exception {
        InitConfig config = initialConfig;
        LOG.debug("Executing provisioner init: workspaceRefId={} projectName={}",
                config.getWorkspaceRefId(), config.getProjectName());

        ProjectLayout projectLayout = layoutProvisioner.provisionProject(config);

        // Second configuration pass. The published files live in folders named after the workspace
        // and the project, so they cannot be found until the layout above has been resolved from
        // the container's own layers.
        List<VolumeConfigLayer> volumeLayers = VolumeConfigLayers.discover(projectLayout);
        config = config.withVolumeLayers(volumeLayers);

        SessionRegistrar registrar = new SessionRegistrar(
                new FileSystemRepository(clock, projectLayout.workspace()), layoutProvisioner);
        ProvisionedSession session = registrar.openSession(config, projectLayout);

        // Phase two of placeholder resolution: the layout only exists now that the session
        // directory has been created, so this is where <<JEFFREY:...>> becomes answerable.
        // Values carrying <<ENV:...>> were already resolved when the configuration was read.
        Placeholders placeholders = Placeholders.of(
                JeffreyPlaceholderSource.of(session.layout(), config.getProfilerPath(),
                        EnvFileBuilder.DEFAULT_FILE_TEMPLATE));

        String features = JvmFeatures.of(config)
                .render(session.layout().session(), placeholders);

        ProfilerCommandResolver.ResolvedProfilerCommand resolvedCommand = profilerCommandResolver.resolve(
                config.getAsprofSettings(),
                config.getProfilerCommandSource(),
                placeholders,
                features);

        registrar.recordSession(config, session, resolvedCommand);
        outputWriter.write(config, session.layout(), resolvedCommand.command());

        // Single greppable verdict line — the one place that tells a user their setup works
        LOG.info("Jeffrey profiling ENABLED: project={} workspace={} instance={} session={} "
                        + "profiler_source={} config_layers={} arg_file={}",
                config.getProjectName(), config.getWorkspaceRefId(), session.instanceId(), session.sessionId(),
                resolvedCommand.source(), config.getAppliedConfigLayers().size(), config.getArgFilePath());
    }

}
