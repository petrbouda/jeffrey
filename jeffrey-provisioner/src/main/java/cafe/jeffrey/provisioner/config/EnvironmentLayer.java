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

package cafe.jeffrey.provisioner.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The {@code JEFFREY_*} environment variables as a HOCON configuration layer.
 *
 * <p>This is the whole environment contract in one table: which variable feeds which setting, and
 * how its text is read. Precedence is expressed by where the layer sits in the merge rather than by
 * a check at each read site — configuration files win over it, built-in defaults lose to it.
 *
 * <p>Every setting is reachable from the environment, so a container never needs to mount a file.
 * The single deliberate omission is {@code JEFFREY_PROFILER_CONFIG}: the generated {@code .env}
 * file <em>exports</em> a variable of that name, and accepting it as an input would feed a previous
 * run's fully-resolved command back in.
 */
public abstract class EnvironmentLayer {

    static final String ORIGIN_DESCRIPTION = "JEFFREY_* environment variables";

    /**
     * Spelled exactly as the agent's own parameter ({@code -javaagent:...=tracing.enabled=true}):
     * this is the switch for that parameter and nothing else, so the two names match rather than
     * needing a translation table. The UI feature and its REST path stay "method-tracing".
     */
    private static final String ENV_TRACING_ENABLED = "JEFFREY_TRACING_ENABLED";

    private static final List<EnvBinding> BINDINGS = List.of(
            new EnvBinding.Value("JEFFREY_HOME", ConfigPaths.JEFFREY_HOME),
            new EnvBinding.Value("JEFFREY_WORKSPACES_DIR", ConfigPaths.WORKSPACES_DIR),
            new EnvBinding.Value("JEFFREY_PROFILER_PATH", ConfigPaths.PROFILER_PATH),
            new EnvBinding.Value("JEFFREY_AGENT_PATH", ConfigPaths.AGENT_PATH),
            new EnvBinding.Value("JEFFREY_REPOSITORY_TYPE", ConfigPaths.REPOSITORY_TYPE),
            new EnvBinding.Value("JEFFREY_ARG_FILE", ConfigPaths.ARG_FILE),
            new EnvBinding.Value("JEFFREY_ENV_FILE", ConfigPaths.ENV_FILE),
            new EnvBinding.Value("JEFFREY_ADDITIONAL_JVM_OPTIONS", ConfigPaths.ADDITIONAL_JVM_OPTIONS),
            new EnvBinding.Value("JEFFREY_PROJECT_NAME", ConfigPaths.PROJECT_NAME),
            new EnvBinding.Value("JEFFREY_PROJECT_LABEL", ConfigPaths.PROJECT_LABEL),
            new EnvBinding.Value("JEFFREY_WORKSPACE_REF_ID", ConfigPaths.PROJECT_WORKSPACE_REF_ID),
            new EnvBinding.Value("JEFFREY_INSTANCE_NAME", ConfigPaths.PROJECT_INSTANCE_NAME),
            new EnvBinding.Flag("JEFFREY_PRINT_ENV", ConfigPaths.PRINT_ENV),
            new EnvBinding.Flag("JEFFREY_PROVISIONER_VERBOSE", ConfigPaths.PROVISIONER_VERBOSE),
            new EnvBinding.Flag("JEFFREY_PERF_COUNTERS", ConfigPaths.PERF_COUNTERS_ENABLED),
            new EnvBinding.Flag(ENV_TRACING_ENABLED, ConfigPaths.TRACING_ENABLED),
            new EnvBinding.Flag("JEFFREY_JDK_JAVA_OPTIONS", ConfigPaths.JDK_JAVA_OPTIONS_ENABLED),
            new EnvBinding.Flag("JEFFREY_DEBUG_NON_SAFEPOINTS", ConfigPaths.DEBUG_NON_SAFEPOINTS_ENABLED),
            new EnvBinding.Attributes("JEFFREY_ATTRIBUTES", ConfigPaths.ATTRIBUTES),
            new EnvBinding.HeapDump("JEFFREY_HEAP_DUMP", ConfigPaths.HEAP_DUMP_ENABLED, ConfigPaths.HEAP_DUMP_TYPE),
            new EnvBinding.JvmLogging("JEFFREY_JVM_LOGGING", ConfigPaths.JVM_LOGGING_ENABLED, ConfigPaths.JVM_LOGGING_COMMAND));
    /**
     * Reads every bound variable that is set. A variable that is absent, blank, or carries a value
     * its binding cannot read contributes nothing, leaving the setting to the layer below.
     */
    public static Config of(Function<String, String> envLookup) {
        // Declaration order, so a generated config reads like the binding table above.
        Map<String, Object> entries = new LinkedHashMap<>();
        for (EnvBinding binding : BINDINGS) {
            String rawValue = envLookup.apply(binding.envName());
            if (rawValue != null && !rawValue.isBlank()) {
                binding.bind(rawValue, entries);
            }
        }
        return ConfigFactory.parseMap(entries, ORIGIN_DESCRIPTION);
    }
}
