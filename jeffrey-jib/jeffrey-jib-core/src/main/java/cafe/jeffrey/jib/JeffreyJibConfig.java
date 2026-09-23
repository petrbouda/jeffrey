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

package cafe.jeffrey.jib;

/**
 * User-facing configuration for the Jeffrey JIB plugin extension. Populated by JIB's Maven /
 * Gradle plugin DSL via bean-style setters (JIB's reflection-based config binding requires
 * JavaBean accessors, which is why this is a plain class rather than a record).
 *
 * <p>Every string field is optional. Null means "do not set an image-level ENV default for this
 * key" — the wrapper script's hardcoded fallback applies at container start, or the operator
 * provides the value via a pod-level env var.
 *
 * <p>{@code profilerPath} carries a second meaning: setting it declares that the image already
 * provides async-profiler, so the extension does not bake that payload. The provisioner has no
 * such property. It is Jeffrey's own binary and the protocol it writes is the one Jeffrey Hub
 * reads, so the extension always bakes it; which build — native or jar — is chosen by the flavour
 * of the extension declared as the jib plugin dependency, not by configuration.
 */
public class JeffreyJibConfig {

    /**
     * Property keys recognised by {@link JeffreyBuildPlanExtender#applyProperties} when a JIB
     * plugin-extension configuration is expressed via the string {@code properties} DSL rather
     * than the typed {@code configuration(Action<JeffreyJibConfig>)} block. Each constant names
     * the key a consumer writes in build config (e.g. {@code "jeffreyHome"}) and corresponds
     * one-to-one to the setter on this class.
     */
    public static final String ENABLED = "enabled";
    public static final String JEFFREY_HOME = "jeffreyHome";
    public static final String BASE_CONFIG = "baseConfig";
    public static final String OVERRIDE_CONFIG = "overrideConfig";
    public static final String ARG_FILE = "argFile";
    public static final String PROFILER_PATH = "profilerPath";
    public static final String PROJECT_NAME = "projectName";

    private boolean enabled = true;
    private String jeffreyHome;
    private String baseConfig;
    private String overrideConfig;
    private String argFile;
    private String profilerPath;
    private String projectName;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getJeffreyHome() {
        return jeffreyHome;
    }

    public void setJeffreyHome(String jeffreyHome) {
        this.jeffreyHome = jeffreyHome;
    }

    public String getBaseConfig() {
        return baseConfig;
    }

    public void setBaseConfig(String baseConfig) {
        this.baseConfig = baseConfig;
    }

    public String getOverrideConfig() {
        return overrideConfig;
    }

    public void setOverrideConfig(String overrideConfig) {
        this.overrideConfig = overrideConfig;
    }

    public String getArgFile() {
        return argFile;
    }

    public void setArgFile(String argFile) {
        this.argFile = argFile;
    }

    public String getProfilerPath() {
        return profilerPath;
    }

    public void setProfilerPath(String profilerPath) {
        this.profilerPath = profilerPath;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

}
