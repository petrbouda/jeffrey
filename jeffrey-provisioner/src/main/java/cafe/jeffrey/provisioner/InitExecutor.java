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

        String features = JvmFeatures.of(config)
                .render(session.layout().session(), placeholders);

        ProfilerSettingsResolver.ResolvedProfilerSettings resolvedSettings =
                profilerSettingsResolver.resolve(config.getProfilerCommand(), placeholders, features);

        registrar.recordSession(config, session);
        outputWriter.write(config, session.layout(), resolvedSettings.command());

        // Single greppable verdict line — the one place that tells a user their setup works
        LOG.info("Jeffrey profiling ENABLED: project={} workspace={} instance={} session={} profiler_source={} arg_file={}",
                config.getProjectName(), config.getWorkspaceRefId(), session.instanceId(), session.sessionId(),
                resolvedSettings.source(), config.getArgFilePath());
    }

}
